package concert.ticketing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TicketingSubsystemTest {
    @TempDir
    Path dataDir;

    private TicketingService ticketing;

    @BeforeEach
    void freshTicketOffice() {
        ticketing = ticketingFor(dataDir);
    }

    private static TicketingService ticketingFor(Path dir) {
        return new TicketingService(new TicketRepository(dir), new FraudChecker(), new TicketPrinter("test-secret"));
    }

    private Ticket issueTo(String buyer) {
        return ticketing.issue("REF-" + buyer, buyer, buyer + "@example.com", "Seat C7 FLOOR", 9_000);
    }

    @Test
    void anHonestBuyerGetsAnIssuedTicketWithAGenuineBarcode() {
        Ticket ticket = issueTo("alice");

        assertEquals(Ticket.Status.ISSUED, ticket.status());
        assertEquals(16, ticket.barcode().length());
        assertTrue(ticketing.isGenuine(ticket.id()));
        assertTrue(ticketing.print(ticket.id()).contains(ticket.barcode()));
    }

    @Test
    void blocklistedBuyersAreRejectedAndNothingIsPersisted() {
        ticketing.blockBuyer("mallory");

        FraudRejectedException e = assertThrows(FraudRejectedException.class, () -> issueTo("mallory"));

        assertTrue(e.getMessage().contains("blocklist"));
        assertTrue(ticketing.allTickets().isEmpty());
    }

    @Test
    void unblockingABuyerLetsThemBuyAgain() {
        ticketing.blockBuyer("mallory");
        ticketing.unblockBuyer("mallory");

        assertEquals(Ticket.Status.ISSUED, issueTo("mallory").status());
        assertTrue(ticketing.blocklist().isEmpty());
    }

    @Test
    void invalidAndDisposableEmailsAreRejected() {
        assertThrows(FraudRejectedException.class,
                () -> ticketing.issue("R", "bob", "not-an-email", "x", 1));
        assertThrows(FraudRejectedException.class,
                () -> ticketing.issue("R", "bob", "bob@mailinator.com", "x", 1));
        assertThrows(FraudRejectedException.class,
                () -> ticketing.issue("R", "bob", null, "x", 1));
    }

    @Test
    void scalpersAreCappedAtFourLiveTickets() {
        for (int i = 0; i < FraudChecker.MAX_LIVE_TICKETS_PER_BUYER; i++) {
            issueTo("scalper");
        }
        assertThrows(FraudRejectedException.class, () -> issueTo("scalper"));
    }

    @Test
    void voidedTicketsNoLongerCountTowardsTheCap() {
        for (int i = 0; i < FraudChecker.MAX_LIVE_TICKETS_PER_BUYER; i++) {
            Ticket t = issueTo("scalper");
            if (i == 0) {
                ticketing.voidTicket(t.id(), "changed mind");
            }
        }
        assertEquals(Ticket.Status.ISSUED, issueTo("scalper").status());
    }

    @Test
    void ticketLifecycleIsEnforcedAndIdempotent() {
        Ticket ticket = issueTo("alice");

        assertEquals(Ticket.Status.ACTIVATED, ticketing.activate(ticket.id()).status());
        assertEquals(Ticket.Status.ACTIVATED, ticketing.activate(ticket.id()).status());
        assertEquals(Ticket.Status.VOIDED, ticketing.voidTicket(ticket.id(), "refund").status());
        assertEquals("refund", ticketing.voidTicket(ticket.id(), "again").voidReason());
        assertThrows(IllegalStateException.class, () -> ticketing.activate(ticket.id()));
    }

    @Test
    void ticketsAndBlocklistSurviveARestart() {
        Ticket ticket = issueTo("alice");
        ticketing.blockBuyer("mallory");

        TicketingService afterRestart = ticketingFor(dataDir);

        assertEquals(ticket.barcode(), afterRestart.findByReference("REF-alice").orElseThrow().barcode());
        assertTrue(afterRestart.find(ticket.id()).isPresent());
        assertTrue(afterRestart.blocklist().contains("mallory"));
        assertThrows(IllegalArgumentException.class, () -> afterRestart.print("T-missing"));
    }

    @Test
    void forgedBarcodesAreDetected() {
        TicketPrinter printer = new TicketPrinter("test-secret");
        assertEquals(printer.barcodeFor("T-1", "alice"), printer.barcodeFor("T-1", "alice"));
        assertNotEquals(printer.barcodeFor("T-1", "alice"), new TicketPrinter("other").barcodeFor("T-1", "alice"));
    }
}
