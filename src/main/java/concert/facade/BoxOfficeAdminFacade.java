package concert.facade;

import concert.access.AccessPass;
import concert.access.AccessService;
import concert.access.RefundService;
import concert.seating.SeatHold;
import concert.seating.SeatingService;
import concert.ticketing.Ticket;
import concert.ticketing.TicketingService;

import java.util.List;
import java.util.Optional;

/** Admin facade: inspect, cancel/refund, manual compensation, venue ops. */
public class BoxOfficeAdminFacade {
    public record OrderInspection(OrderRecord order, String seatHold, String ticket, String accessPass,
                                  String refund) {
    }

    public record GateStatus(String gateId, List<String> zones, boolean open, int passes, int capacity) {
    }

    public record Cancellation(String orderId, long refundCents, int refundPercent) {
    }

    private static final String NONE = "(nothing on disk)";

    private final SeatingService seating;
    private final TicketingService ticketing;
    private final AccessService access;
    private final OrderJournal journal;
    private final ChainCompensator compensator;

    public BoxOfficeAdminFacade(SeatingService seating, TicketingService ticketing, AccessService access,
                                OrderJournal journal, ChainCheckpointListener listener) {
        this.seating = seating;
        this.ticketing = ticketing;
        this.access = access;
        this.journal = journal;
        this.compensator = new ChainCompensator(seating, ticketing, access, journal, listener);
    }

    public List<OrderRecord> orders() {
        return journal.all();
    }

    public List<OrderRecord> interruptedOrders() {
        return journal.unfinished();
    }

    public OrderInspection inspect(String orderId) {
        OrderRecord order = journal.require(orderId);
        String ref = order.orderId();
        String refund = access.findByReference(ref)
                .flatMap(pass -> access.refunds().stream().filter(r -> r.passId().equals(pass.id())).findFirst())
                .map(RefundService.Refund::toString).orElse(NONE);
        return new OrderInspection(order,
                seating.findByReference(ref).map(SeatHold::toString).orElse(NONE),
                ticketing.findByReference(ref).map(Ticket::toString).orElse(NONE),
                access.findByReference(ref).map(AccessPass::toString).orElse(NONE),
                refund);
    }

    public String printTicket(String orderId) {
        OrderRecord order = journal.require(orderId);
        if (order.ticketId() == null) {
            throw new IllegalStateException("Order " + order.orderId() + " never got a ticket");
        }
        return ticketing.print(order.ticketId());
    }

    public List<ChainStep> compensateManually(String orderId, String reason) {
        OrderRecord order = journal.require(orderId);
        if (!order.status().isUnfinished()) {
            throw new IllegalStateException("Order " + order.orderId() + " is " + order.status()
                    + "; only unfinished orders can be compensated manually");
        }
        if (order.status() == ChainStatus.COMPENSATING) {
            return compensator.finish(order);
        }
        ChainStep interruptedAt = switch (order.status()) {
            case PENDING_A -> ChainStep.SEATING;
            case PENDING_B -> ChainStep.TICKETING;
            default -> ChainStep.ACCESS;
        };
        return compensator.compensate(order, interruptedAt, "manual compensation: " + reason);
    }

    public Cancellation cancelCompletedOrder(String orderId) {
        OrderRecord order = journal.require(orderId);
        if (order.status() != ChainStatus.COMPLETED) {
            throw new IllegalStateException("Order " + order.orderId() + " is " + order.status()
                    + "; only COMPLETED orders can be cancelled");
        }
        RefundService.Refund refund = access.refund(order.passId());
        order.note("C: pass " + order.passId() + " revoked, refund " + refund.amountCents() + "c (" + refund.percent() + "%)");
        journal.save(order);
        ticketing.voidTicket(order.ticketId(), "order cancelled");
        order.note("B: ticket " + order.ticketId() + " voided");
        journal.save(order);
        seating.release(order.holdId(), "order cancelled");
        order.note("A: seat " + order.seatCode() + " released");
        order.cancelled(refund.amountCents());
        journal.save(order);
        return new Cancellation(order.orderId(), refund.amountCents(), refund.percent());
    }

    public void blockBuyer(String buyerId) {
        ticketing.blockBuyer(buyerId);
    }

    public void unblockBuyer(String buyerId) {
        ticketing.unblockBuyer(buyerId);
    }

    public List<String> blocklist() {
        return ticketing.blocklist();
    }

    public void closeGate(String gateId) {
        access.closeGate(gateId);
    }

    public void openGate(String gateId) {
        access.openGate(gateId);
    }

    public void setGateCapacity(String gateId, int capacity) {
        access.setCapacity(gateId, capacity);
    }

    public List<GateStatus> gates() {
        return access.gates().stream()
                .map(g -> new GateStatus(g.id(), g.zones(), g.open(), access.passesAt(g.id()), g.capacity()))
                .toList();
    }

    public String admit(String orderId) {
        OrderRecord order = journal.require(orderId);
        if (order.status() != ChainStatus.COMPLETED) {
            throw new IllegalStateException("Order " + order.orderId() + " is " + order.status() + ", not admitted");
        }
        return access.scan(order.passId()).toString();
    }

    public Optional<OrderRecord> find(String orderId) {
        return journal.find(orderId);
    }
}
