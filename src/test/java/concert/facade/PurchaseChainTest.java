package concert.facade;

import concert.access.AccessPass;
import concert.ticketing.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static concert.facade.ChainStep.ACCESS;
import static concert.facade.ChainStep.SEATING;
import static concert.facade.ChainStep.TICKETING;
import static org.junit.jupiter.api.Assertions.*;

class PurchaseChainTest {
    @TempDir
    Path emptyDir;

    private ConcertVenue venue;

    @BeforeEach
    void openTheBoxOffice() {
        venue = ConcertVenue.freshVenueIn(emptyDir);
    }

    @Test
    void aFanBuysASeatAndAllThreeSubsystemsCommit() {
        PurchaseReceipt receipt = venue.fan("alice").buysSeat("C7").succeeds();

        assertEquals("FLOOR", receipt.zone());
        assertEquals(BoxOffice.MAIN_GATE, receipt.gateId());
        assertEquals(9_000, receipt.priceCents());
        venue.seat("C7").isSoldTo("alice");
        venue.order(receipt.orderId()).hasTicket(Ticket.Status.ACTIVATED).hasPass(AccessPass.Status.ACTIVE);
    }

    @Test
    void fraudRejectionAfterTheSeatWasHeldUndoesTheSeatHoldOnly() {
        venue.buyerIsBlocklisted("mallory");

        ConcertVenue.Attempt attempt = venue.fan("mallory").buysSeat("C5")
                .failsAt(TICKETING)
                .afterUndoing(SEATING);

        venue.seat("C5").isFreeAgain();
        venue.order(attempt.orderId()).hasNoTicket().hasNoPass().undidInThisOrder(SEATING);
    }

    @Test
    void closedGateAfterSeatAndTicketCommittedUndoesTicketThenSeat() {
        venue.gateIsClosed(BoxOffice.BALCONY_GATE);

        ConcertVenue.Attempt attempt = venue.fan("bob").buysSeat("G3")
                .failsAt(ACCESS)
                .afterUndoing(TICKETING, SEATING);

        venue.seat("G3").isFreeAgain();
        venue.order(attempt.orderId())
                .hasTicket(Ticket.Status.VOIDED)
                .hasNoPass()
                .undidInThisOrder(TICKETING, SEATING);
    }

    @Test
    void fullGateAlsoTriggersTheTwoStepRollback() {
        venue.admin().setGateCapacity(BoxOffice.BALCONY_GATE, 1);
        venue.fan("alice").buysSeat("G1").succeeds();

        venue.fan("bob").buysSeat("G2").failsAt(ACCESS).afterUndoing(TICKETING, SEATING);

        venue.seat("G2").isFreeAgain();
        venue.seat("G1").isSoldTo("alice");
    }

    @Test
    void aSeatThatIsAlreadySoldFailsAtTheFirstStepWithNothingToUndo() {
        venue.fan("alice").buysSeat("A1").succeeds();

        venue.fan("bob").buysSeat("A1").failsAt(SEATING).withNothingToUndo();

        venue.seat("A1").isSoldTo("alice");
    }

    @Test
    void aSeatThatDoesNotExistFailsAtTheFirstStep() {
        venue.fan("bob").buysSeat("Z42").failsAt(SEATING).withNothingToUndo();
    }

    @Test
    void disposableEmailIsCaughtByFraudCheckAndTheSeatIsReleased() {
        venue.fan("carol").buysSeatUsing("carol@mailinator.com", "B4").failsAt(TICKETING).afterUndoing(SEATING);

        venue.seat("B4").isFreeAgain();
    }

    @Test
    void malformedRequestsAreRejectedBeforeAnythingIsRecorded() {
        TicketPurchaseFacade purchases = venue.purchases();

        assertThrows(InvalidPurchaseException.class, () -> purchases.purchase(null));
        assertThrows(InvalidPurchaseException.class, () -> purchases.purchase(new PurchaseRequest(" ", "a@b.com", "C1")));
        assertThrows(InvalidPurchaseException.class, () -> purchases.purchase(new PurchaseRequest("bad id!", "a@b.com", "C1")));
        assertThrows(InvalidPurchaseException.class, () -> purchases.purchase(new PurchaseRequest("dave", "", "C1")));
        assertThrows(InvalidPurchaseException.class, () -> purchases.purchase(new PurchaseRequest("dave", "d@x.com", null)));
        assertTrue(venue.admin().orders().isEmpty());
    }

    @Test
    void afterCompensationTheSameSeatCanBeBoughtByAnHonestFan() {
        venue.buyerIsBlocklisted("mallory");
        venue.fan("mallory").buysSeat("C5").failsAt(TICKETING);

        venue.fan("alice").buysSeat("C5").succeeds();

        venue.seat("C5").isSoldTo("alice");
    }

    @Test
    void offersShowFreeSeatsWithLivePrices() {
        venue.fan("alice").buysSeat("A1").succeeds();

        Map<String, Map<String, Long>> offers = venue.purchases().seatOffers();

        assertFalse(offers.get("VIP").containsKey("A1"));
        assertEquals(15, offers.get("VIP").size());
        assertEquals(4_500, offers.get("BALCONY").get("G1"));
        assertEquals(15_000, venue.purchases().quoteCents("A2"));
    }

    @Test
    void rejectedOrdersCannotBeResumedButCompletedOnesJustReturnTheirReceipt() {
        String rejected = venue.fan("bob").buysSeat("Z1").orderId();
        PurchaseReceipt done = venue.fan("alice").buysSeat("C1").succeeds();

        assertThrows(IllegalStateException.class, () -> venue.purchases().resume(rejected));
        assertEquals(done, venue.purchases().resume(done.orderId()));
    }
}
