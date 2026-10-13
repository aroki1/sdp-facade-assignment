package concert.facade;

import concert.access.AccessPass;
import concert.ticketing.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static concert.facade.ChainStep.SEATING;
import static concert.facade.ChainStep.TICKETING;
import static org.junit.jupiter.api.Assertions.*;

class AdminFacadeTest {
    @TempDir
    Path emptyDir;

    private ConcertVenue venue;
    private BoxOfficeAdminFacade admin;

    @BeforeEach
    void openTheBoxOffice() {
        venue = ConcertVenue.freshVenueIn(emptyDir);
        admin = venue.admin();
    }

    @Test
    void inspectionShowsWhatEverySubsystemPersistedForTheOrder() {
        String orderId = venue.fan("alice").buysSeat("C2").succeeds().orderId();

        BoxOfficeAdminFacade.OrderInspection view = admin.inspect(orderId);

        assertEquals(ChainStatus.COMPLETED, view.order().status());
        assertTrue(view.seatHold().contains("CONFIRMED"));
        assertTrue(view.ticket().contains("ACTIVATED"));
        assertTrue(view.accessPass().contains("ACTIVE"));
        assertTrue(view.refund().contains("nothing"));
        assertTrue(admin.printTicket(orderId).contains("C2"));
    }

    @Test
    void cancellingACompletedOrderRefundsAndUnwindsAllThreeSubsystems() {
        String orderId = venue.fan("alice").buysSeat("C2").succeeds().orderId();

        BoxOfficeAdminFacade.Cancellation cancellation = admin.cancelCompletedOrder(orderId);

        assertEquals(100, cancellation.refundPercent());
        assertEquals(9_000, cancellation.refundCents());
        venue.order(orderId).isPersistedAs(ChainStatus.CANCELLED)
                .hasTicket(Ticket.Status.VOIDED).hasPass(AccessPass.Status.REVOKED);
        venue.seat("C2").isFreeAgain();
        assertTrue(admin.inspect(orderId).refund().contains("9000"));
    }

    @Test
    void aFanWhoAlreadyWalkedInCannotBeRefunded() {
        String orderId = venue.fan("alice").buysSeat("C2").succeeds().orderId();
        assertTrue(admin.admit(orderId).contains("USED"));

        assertThrows(IllegalStateException.class, () -> admin.cancelCompletedOrder(orderId));
        venue.seat("C2").isSoldTo("alice");
    }

    @Test
    void onlyCompletedOrdersCanBeCancelledOrAdmitted() {
        venue.buyerIsBlocklisted("mallory");
        String orderId = venue.fan("mallory").buysSeat("C2").orderId();

        assertThrows(IllegalStateException.class, () -> admin.cancelCompletedOrder(orderId));
        assertThrows(IllegalStateException.class, () -> admin.admit(orderId));
        assertThrows(IllegalStateException.class, () -> admin.printTicket(orderId));
        assertThrows(IllegalArgumentException.class, () -> admin.inspect("ORD-NOPE"));
    }

    @Test
    void aStuckOrderCanBeCompensatedManuallyInsteadOfResumed() {
        venue.systemCrashesWhenAnOrderReaches(ChainStatus.PENDING_C);
        assertThrows(ConcertVenue.SimulatedCrash.class,
                () -> venue.purchases().purchase(new PurchaseRequest("alice", "alice@example.com", "B7")));
        venue.restart();
        String orderId = venue.admin().interruptedOrders().get(0).orderId();

        List<ChainStep> undone = venue.admin().compensateManually(orderId, "fan called to cancel");

        assertEquals(List.of(TICKETING, SEATING), undone);
        venue.seat("B7").isFreeAgain();
        venue.order(orderId).isPersistedAs(ChainStatus.COMPENSATED);
        assertThrows(IllegalStateException.class, () -> venue.admin().compensateManually(orderId, "again"));
    }

    @Test
    void manualCompensationAlsoFinishesAnInterruptedCompensationAndHandlesPendingA() {
        venue.buyerIsBlocklisted("mallory");
        venue.systemCrashesWhenAnOrderReaches(ChainStatus.COMPENSATING);
        assertThrows(ConcertVenue.SimulatedCrash.class,
                () -> venue.purchases().purchase(new PurchaseRequest("mallory", "m@example.com", "D1")));
        String orderId = venue.restart().admin().interruptedOrders().get(0).orderId();

        assertEquals(List.of(SEATING), venue.admin().compensateManually(orderId, "operator"));

        OrderRecord pendingA = new OrderJournal(emptyDir).open("zed", "zed@example.com", "D3");
        assertEquals(List.of(), venue.admin().compensateManually(pendingA.orderId(), "never started"));
        venue.order(pendingA.orderId()).isPersistedAs(ChainStatus.COMPENSATED);
    }

    @Test
    void blocklistAndGatesAreOperatedThroughTheAdminFacade() {
        admin.blockBuyer("mallory");
        assertEquals(List.of("mallory"), admin.blocklist());
        admin.unblockBuyer("mallory");
        assertTrue(admin.blocklist().isEmpty());

        venue.gateIsClosed(BoxOffice.MAIN_GATE);
        assertFalse(admin.gates().get(0).open());
        venue.gateReopens(BoxOffice.MAIN_GATE);
        venue.fan("alice").buysSeat("A3").succeeds();

        BoxOfficeAdminFacade.GateStatus main = admin.gates().get(0);
        assertTrue(main.open());
        assertEquals(1, main.passes());
        assertEquals(56, main.capacity());
        assertEquals(1, admin.orders().size());
    }
}
