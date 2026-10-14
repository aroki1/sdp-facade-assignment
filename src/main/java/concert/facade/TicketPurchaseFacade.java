package concert.facade;

import concert.access.AccessDeniedException;
import concert.access.AccessPass;
import concert.access.AccessService;
import concert.seating.SeatHold;
import concert.seating.SeatUnavailableException;
import concert.seating.SeatingChart;
import concert.seating.SeatingService;
import concert.ticketing.FraudRejectedException;
import concert.ticketing.Ticket;
import concert.ticketing.TicketingService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Client facade: runs A -> B -> C and compensates on failure. */
public class TicketPurchaseFacade {
    private static final Pattern BUYER_ID = Pattern.compile("[A-Za-z0-9_.-]{2,32}");

    private final SeatingService seating;
    private final TicketingService ticketing;
    private final AccessService access;
    private final OrderJournal journal;
    private final ChainCompensator compensator;
    private final ChainCheckpointListener listener;

    public TicketPurchaseFacade(SeatingService seating, TicketingService ticketing, AccessService access,
                                OrderJournal journal, ChainCheckpointListener listener) {
        this.seating = seating;
        this.ticketing = ticketing;
        this.access = access;
        this.journal = journal;
        this.listener = listener;
        this.compensator = new ChainCompensator(seating, ticketing, access, journal, listener);
    }

    public Map<String, Map<String, Long>> seatOffers() {
        Map<String, Map<String, Long>> offers = new LinkedHashMap<>();
        for (SeatingChart.Section section : SeatingChart.Section.values()) {
            Map<String, Long> seats = new LinkedHashMap<>();
            List<String> free = seating.availableSeats(section);
            long price = free.isEmpty() ? 0 : seating.quoteCents(free.get(0));
            free.forEach(seat -> seats.put(seat, price));
            offers.put(section.name(), seats);
        }
        return offers;
    }

    public long quoteCents(String seatCode) {
        return seating.quoteCents(seatCode);
    }

    public PurchaseReceipt purchase(PurchaseRequest request) {
        validate(request);
        OrderRecord order = journal.open(request.buyerId().trim(), request.email().trim(),
                request.seatCode().trim().toUpperCase());
        return drive(order);
    }

    public PurchaseReceipt resume(String orderId) {
        return drive(journal.require(orderId));
    }

    public record ResumeOutcome(String orderId, ChainStatus statusBefore, ChainStatus statusAfter, String detail) {
    }

    public List<ResumeOutcome> resumeInterrupted() {
        List<ResumeOutcome> outcomes = new ArrayList<>();
        for (OrderRecord order : journal.unfinished()) {
            ChainStatus before = order.status();
            String detail;
            try {
                PurchaseReceipt receipt = drive(order);
                detail = "completed, pass " + receipt.passId() + " at " + receipt.gateId();
            } catch (PurchaseFailedException e) {
                detail = e.getMessage();
            }
            outcomes.add(new ResumeOutcome(order.orderId(), before, journal.require(order.orderId()).status(), detail));
        }
        return outcomes;
    }

    private PurchaseReceipt drive(OrderRecord order) {
        while (true) {
            switch (order.status()) {
                case PENDING_A -> holdSeat(order);
                case PENDING_B -> issueTicket(order);
                case PENDING_C -> grantAccess(order);
                case COMPLETED -> {
                    return receiptFor(order);
                }
                case COMPENSATING -> {
                    List<ChainStep> undone = compensator.finish(order);
                    throw new PurchaseFailedException(order.orderId(), order.failedStep(), undone,
                            order.failureReason(), null);
                }
                default -> throw new IllegalStateException(
                        "Order " + order.orderId() + " is already final: " + order.status());
            }
        }
    }

    private void holdSeat(OrderRecord order) {
        SeatHold hold;
        try {
            hold = seating.findByReference(order.orderId()).filter(SeatHold::isActive)
                    .orElseGet(() -> seating.hold(order.seatCode(), order.buyerId(), order.orderId()));
        } catch (SeatUnavailableException | IllegalArgumentException e) {
            order.rejected(ChainStep.SEATING, e.getMessage());
            journal.save(order);
            throw new PurchaseFailedException(order.orderId(), ChainStep.SEATING, List.of(), e.getMessage(), e);
        }
        order.seatHeld(hold.id(), hold.priceCents(), hold.section().name());
        checkpoint(order);
    }

    private void issueTicket(OrderRecord order) {
        requireStillHeld(order, ChainStep.TICKETING);
        Ticket ticket;
        try {
            ticket = ticketing.findByReference(order.orderId()).filter(Ticket::isLive)
                    .orElseGet(() -> ticketing.issue(order.orderId(), order.buyerId(), order.email(),
                            "Seat " + order.seatCode() + " - " + order.zone(), order.priceCents()));
        } catch (FraudRejectedException e) {
            throw failAndCompensate(order, ChainStep.TICKETING, e);
        }
        order.ticketIssued(ticket.id());
        checkpoint(order);
    }

    private void grantAccess(OrderRecord order) {
        requireStillHeld(order, ChainStep.ACCESS);
        Optional<Ticket> ticket = ticketing.find(order.ticketId());
        if (ticket.isEmpty() || !ticket.get().isLive()) {
            throw failAndCompensate(order, ChainStep.ACCESS,
                    new IllegalStateException("ticket " + order.ticketId() + " is no longer live on disk"));
        }
        AccessPass pass;
        try {
            pass = access.findByReference(order.orderId())
                    .filter(p -> p.status() == AccessPass.Status.ACTIVE)
                    .orElseGet(() -> access.grant(order.orderId(), order.buyerId(), order.zone(), order.priceCents()));
        } catch (AccessDeniedException e) {
            throw failAndCompensate(order, ChainStep.ACCESS, e);
        }
        seating.confirm(order.holdId());
        ticketing.activate(order.ticketId());
        order.completed(pass.id(), pass.gateId());
        checkpoint(order);
    }

    private void requireStillHeld(OrderRecord order, ChainStep step) {
        Optional<SeatHold> hold = seating.find(order.holdId());
        if (hold.isEmpty() || !hold.get().isActive()) {
            throw failAndCompensate(order, step, new IllegalStateException(
                    "seat hold " + order.holdId() + " is " + hold.map(h -> h.status().name()).orElse("missing") + " on disk"));
        }
    }

    private PurchaseFailedException failAndCompensate(OrderRecord order, ChainStep step, RuntimeException cause) {
        List<ChainStep> undone = compensator.compensate(order, step, cause.getMessage());
        return new PurchaseFailedException(order.orderId(), step, undone, cause.getMessage(), cause);
    }

    private void checkpoint(OrderRecord order) {
        journal.save(order);
        listener.checkpointed(order);
    }

    private PurchaseReceipt receiptFor(OrderRecord order) {
        String barcode = ticketing.find(order.ticketId()).map(Ticket::barcode).orElse("?");
        return new PurchaseReceipt(order.orderId(), order.seatCode(), order.zone(), order.priceCents(),
                order.ticketId(), barcode, order.passId(), order.gateId());
    }

    private static void validate(PurchaseRequest request) {
        if (request == null) {
            throw new InvalidPurchaseException("No purchase request given");
        }
        if (request.buyerId() == null || !BUYER_ID.matcher(request.buyerId().trim()).matches()) {
            throw new InvalidPurchaseException("Buyer id must be 2-32 letters, digits, '.', '_' or '-'");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new InvalidPurchaseException("An e-mail address is required");
        }
        if (request.seatCode() == null || request.seatCode().isBlank()) {
            throw new InvalidPurchaseException("A seat code (e.g. C7) is required");
        }
    }
}
