package concert.ticketing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketingSubsystemTest {
    @Test
    void anHonestBuyerIsApprovedButBlocklistsBadEmailsAndHoardingAreRejected() {
        FraudChecker checker = new FraudChecker();

        assertTrue(checker.assess("alice", "alice@example.com", false, 0).approved());
        assertFalse(checker.assess("mallory", "mallory@example.com", true, 0).approved());
        assertFalse(checker.assess("bob", "not-an-email", false, 0).approved());
        assertFalse(checker.assess("bob", "bob@mailinator.com", false, 0).approved());
        assertFalse(checker.assess("scalper", "scalper@example.com", false,
                FraudChecker.MAX_LIVE_TICKETS_PER_BUYER).approved());
    }

    @Test
    void forgedBarcodesAreDetected() {
        TicketPrinter printer = new TicketPrinter("test-secret");
        assertEquals(printer.barcodeFor("T-1", "alice"), printer.barcodeFor("T-1", "alice"));
        assertNotEquals(printer.barcodeFor("T-1", "alice"), new TicketPrinter("other").barcodeFor("T-1", "alice"));
    }
}
