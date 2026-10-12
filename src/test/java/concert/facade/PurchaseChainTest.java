package concert.facade;

import concert.access.AccessPass;
import concert.ticketing.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static concert.facade.ChainStep.SEATING;
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
    void offersShowFreeSeatsWithLivePrices() {
        venue.fan("alice").buysSeat("A1").succeeds();

        Map<String, Map<String, Long>> offers = venue.purchases().seatOffers();

        assertFalse(offers.get("VIP").containsKey("A1"));
        assertEquals(15, offers.get("VIP").size());
        assertEquals(4_500, offers.get("BALCONY").get("G1"));
        assertEquals(15_000, venue.purchases().quoteCents("A2"));
    }
}
