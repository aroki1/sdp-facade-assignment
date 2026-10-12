package concert.facade;

import concert.access.AccessPass;
import concert.access.AccessService;
import concert.seating.SeatingService;
import concert.ticketing.TicketingService;

import java.util.List;

/** Undoes committed steps newest-first, based on what is on disk. */
final class ChainCompensator {
    private final SeatingService seating;
    private final TicketingService ticketing;
    private final AccessService access;
    private final OrderJournal journal;
    private final ChainCheckpointListener listener;

    ChainCompensator(SeatingService seating, TicketingService ticketing, AccessService access,
                     OrderJournal journal, ChainCheckpointListener listener) {
        this.seating = seating;
        this.ticketing = ticketing;
        this.access = access;
        this.journal = journal;
        this.listener = listener;
    }

    List<ChainStep> compensate(OrderRecord order, ChainStep failedStep, String reason) {
        order.compensating(failedStep, reason);
        journal.save(order);
        listener.checkpointed(order);
        return finish(order);
    }

    List<ChainStep> finish(OrderRecord order) {
        String why = "compensation for " + order.orderId() + ": " + order.failedStep() + " failed";

        access.findByReference(order.orderId()).ifPresent(pass -> {
            if (pass.status() == AccessPass.Status.ACTIVE) {
                access.revoke(pass.id(), why);
            }
            order.undone(ChainStep.ACCESS, "pass " + pass.id() + " is " + AccessPass.Status.REVOKED);
            journal.save(order);
        });

        ticketing.findByReference(order.orderId()).ifPresent(ticket -> {
            ticketing.voidTicket(ticket.id(), why);
            order.undone(ChainStep.TICKETING, "ticket " + ticket.id() + " voided");
            journal.save(order);
        });

        seating.findByReference(order.orderId()).ifPresent(hold -> {
            seating.release(hold.id(), why);
            order.undone(ChainStep.SEATING, "seat " + hold.seatCode() + " released (hold " + hold.id() + ")");
            journal.save(order);
        });

        order.compensated();
        journal.save(order);
        return order.compensatedSteps();
    }
}
