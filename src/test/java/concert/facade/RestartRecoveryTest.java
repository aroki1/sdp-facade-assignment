package concert.facade;

import concert.access.AccessPass;
import concert.seating.SeatHoldRepository;
import concert.seating.SeatingChart;
import concert.seating.SeatingService;
import concert.seating.DynamicPricingEngine;
import concert.ticketing.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static concert.facade.ChainStep.ACCESS;
import static concert.facade.ChainStep.SEATING;
import static concert.facade.ChainStep.TICKETING;
import static org.junit.jupiter.api.Assertions.*;

class RestartRecoveryTest {
    @TempDir
    Path emptyDir;

    private ConcertVenue venue;

    @BeforeEach
    void openTheBoxOffice() {
        venue = ConcertVenue.freshVenueIn(emptyDir);
    }

    private String orderInterruptedAt(ChainStatus status, String buyer, String seat) {
        venue.systemCrashesWhenAnOrderReaches(status);
        assertThrows(ConcertVenue.SimulatedCrash.class,
                () -> venue.purchases().purchase(new PurchaseRequest(buyer, buyer + "@example.com", seat)));
        return venue.admin().interruptedOrders().get(0).orderId();
    }

    @Test
    void crashAfterSeatHoldIsResumedFromPendingBAfterRestart() {
        String orderId = orderInterruptedAt(ChainStatus.PENDING_B, "alice", "C3");

        venue.restart();
        venue.order(orderId).isPersistedAs(ChainStatus.PENDING_B).hasNoTicket();
        venue.seat("C3").isHeldBy("alice");

        List<TicketPurchaseFacade.ResumeOutcome> outcomes = venue.purchases().resumeInterrupted();

        assertEquals(1, outcomes.size());
        assertEquals(ChainStatus.PENDING_B, outcomes.get(0).statusBefore());
        assertEquals(ChainStatus.COMPLETED, outcomes.get(0).statusAfter());
        venue.seat("C3").isSoldTo("alice");
        venue.order(orderId).hasTicket(Ticket.Status.ACTIVATED).hasPass(AccessPass.Status.ACTIVE);
    }

    @Test
    void crashAfterTicketIsResumedFromPendingCAfterRestart() {
        String orderId = orderInterruptedAt(ChainStatus.PENDING_C, "alice", "A5");

        venue.restart();
        venue.order(orderId).isPersistedAs(ChainStatus.PENDING_C).hasTicket(Ticket.Status.ISSUED).hasNoPass();

        PurchaseReceipt receipt = venue.purchases().resume(orderId);

        assertEquals(orderId, receipt.orderId());
        venue.order(orderId).isPersistedAs(ChainStatus.COMPLETED).hasTicket(Ticket.Status.ACTIVATED);
    }

    @Test
    void gateClosedWhileTheAppWasDownSoResumingCompensatesTicketThenSeat() {
        String orderId = orderInterruptedAt(ChainStatus.PENDING_C, "bob", "H1");

        venue.restart().gateIsClosed(BoxOffice.BALCONY_GATE);
        List<TicketPurchaseFacade.ResumeOutcome> outcomes = venue.purchases().resumeInterrupted();

        assertEquals(ChainStatus.COMPENSATED, outcomes.get(0).statusAfter());
        venue.order(orderId).hasTicket(Ticket.Status.VOIDED).undidInThisOrder(TICKETING, SEATING);
        venue.seat("H1").isFreeAgain();
    }

    @Test
    void crashInTheMiddleOfCompensationIsFinishedAfterRestart() {
        venue.buyerIsBlocklisted("mallory");
        String orderId = orderInterruptedAt(ChainStatus.COMPENSATING, "mallory", "D2");

        venue.restart();
        venue.order(orderId).isPersistedAs(ChainStatus.COMPENSATING);
        venue.seat("D2").isHeldBy("mallory");

        PurchaseFailedException e = assertThrows(PurchaseFailedException.class,
                () -> venue.purchases().resume(orderId));

        assertEquals(TICKETING, e.failedStep());
        assertEquals(List.of(SEATING), e.compensatedSteps());
        venue.order(orderId).isPersistedAs(ChainStatus.COMPENSATED);
        venue.seat("D2").isFreeAgain();
    }

    @Test
    void aSeatHoldWrittenJustBeforeTheCrashIsAdoptedNotDuplicated() {
        OrderRecord order = new OrderJournal(emptyDir).open("alice", "alice@example.com", "E4");
        new SeatingService(new SeatHoldRepository(emptyDir), new SeatingChart(), new DynamicPricingEngine(),
                ConcertVenue.SHOW_DAY, Clock.systemUTC()).hold("E4", "alice", order.orderId());

        venue.restart();
        PurchaseReceipt receipt = venue.purchases().resume(order.orderId());

        assertEquals("E4", receipt.seatCode());
        assertEquals(1, new SeatHoldRepository(emptyDir).findAll().size());
        venue.seat("E4").isSoldTo("alice");
    }

    @Test
    void seatReleasedBehindTheFacadesBackIsDetectedFromDiskAndTheTicketStepIsNotAttempted() {
        String orderId = orderInterruptedAt(ChainStatus.PENDING_B, "alice", "F9");
        SeatingService seatingAlone = new SeatingService(new SeatHoldRepository(emptyDir), new SeatingChart(),
                new DynamicPricingEngine(), ConcertVenue.SHOW_DAY, Clock.systemUTC());
        seatingAlone.release(seatingAlone.findByReference(orderId).orElseThrow().id(), "operator");

        venue.restart();
        PurchaseFailedException e = assertThrows(PurchaseFailedException.class,
                () -> venue.purchases().resume(orderId));

        assertEquals(TICKETING, e.failedStep());
        venue.order(orderId).isPersistedAs(ChainStatus.COMPENSATED).hasNoTicket();
    }

    @Test
    void ticketVoidedBehindTheFacadesBackIsDetectedBeforeGrantingAccess() {
        String orderId = orderInterruptedAt(ChainStatus.PENDING_C, "alice", "C9");
        venue.restart();
        String ticketId = venue.admin().find(orderId).orElseThrow().ticketId();
        new concert.ticketing.TicketingService(new concert.ticketing.TicketRepository(emptyDir),
                new concert.ticketing.FraudChecker(), new concert.ticketing.TicketPrinter("k"))
                .voidTicket(ticketId, "chargeback");

        PurchaseFailedException e = assertThrows(PurchaseFailedException.class,
                () -> venue.purchases().resume(orderId));

        assertEquals(ACCESS, e.failedStep());
        venue.seat("C9").isFreeAgain();
        venue.order(orderId).hasNoPass();
    }

    @Test
    void completedOrdersAreNotTouchedByRecovery() {
        venue.fan("alice").buysSeat("B1").succeeds();

        venue.restart();

        assertTrue(venue.purchases().resumeInterrupted().isEmpty());
        venue.seat("B1").isSoldTo("alice");
    }
}
