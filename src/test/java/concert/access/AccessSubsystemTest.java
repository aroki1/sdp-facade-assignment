package concert.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccessSubsystemTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dataDir;

    private AccessService access;

    @BeforeEach
    void freshVenueEntrance() {
        access = accessFor(dataDir, TODAY.plusDays(20));
        access.configureGate("G1", List.of("VIP", "FLOOR"), 3);
        access.configureGate("G2", List.of("BALCONY"), 1);
    }

    private static AccessService accessFor(Path dir, LocalDate eventDate) {
        return new AccessService(new AccessRepository(dir), new AccessControlGate(), new RefundService(),
                eventDate, CLOCK);
    }

    @Test
    void aPassIsGrantedThroughTheGateServingTheZone() {
        AccessPass pass = access.grant("REF-1", "alice", "FLOOR", 9_000);

        assertEquals("G1", pass.gateId());
        assertEquals(AccessPass.Status.ACTIVE, pass.status());
        assertEquals(1, access.passesAt("G1"));
    }

    @Test
    void aClosedGateDeniesNewPasses() {
        access.closeGate("G2");

        AccessDeniedException e = assertThrows(AccessDeniedException.class,
                () -> access.grant("REF-1", "alice", "BALCONY", 4_500));
        assertTrue(e.getMessage().contains("closed"));
        assertTrue(access.allPasses().isEmpty());
    }

    @Test
    void aFullGateDeniesNewPassesUntilCapacityIsRaised() {
        access.grant("REF-1", "alice", "BALCONY", 4_500);

        assertThrows(AccessDeniedException.class, () -> access.grant("REF-2", "bob", "BALCONY", 4_500));

        access.setCapacity("G2", 2);
        assertEquals("G2", access.grant("REF-2", "bob", "BALCONY", 4_500).gateId());
    }

    @Test
    void revokedPassesFreeCapacity() {
        AccessPass pass = access.grant("REF-1", "alice", "BALCONY", 4_500);
        access.revoke(pass.id(), "cancelled");
        access.revoke(pass.id(), "cancelled twice");

        assertEquals(0, access.passesAt("G2"));
        assertEquals("cancelled", access.find(pass.id()).orElseThrow().revokeReason());
    }

    @Test
    void zonesWithoutAGateAreDenied() {
        assertThrows(AccessDeniedException.class, () -> access.grant("REF-1", "alice", "ROOFTOP", 1));
    }

    @Test
    void aPassCanBeScannedOnlyOnceAndNotThroughAClosedGate() {
        AccessPass pass = access.grant("REF-1", "alice", "VIP", 15_000);
        access.closeGate("G1");
        assertThrows(AccessDeniedException.class, () -> access.scan(pass.id()));

        access.openGate("G1");
        assertEquals(AccessPass.Status.USED, access.scan(pass.id()).status());
        assertThrows(AccessDeniedException.class, () -> access.scan(pass.id()));
    }

    @Test
    void refundRevokesThePassRecordsTheAmountAndIsIdempotent() {
        AccessPass pass = access.grant("REF-1", "alice", "FLOOR", 9_000);

        RefundService.Refund refund = access.refund(pass.id());

        assertEquals(100, refund.percent());
        assertEquals(9_000, refund.amountCents());
        assertEquals(AccessPass.Status.REVOKED, access.find(pass.id()).orElseThrow().status());
        assertEquals(refund, access.refund(pass.id()));
        assertEquals(1, access.refunds().size());
    }

    @Test
    void usedPassesAreNotRefundable() {
        AccessPass pass = access.grant("REF-1", "alice", "FLOOR", 9_000);
        access.scan(pass.id());

        assertThrows(IllegalStateException.class, () -> access.refund(pass.id()));
        assertThrows(IllegalStateException.class, () -> access.revoke(pass.id(), "late"));
    }

    @Test
    void refundPolicyDependsOnDaysBeforeTheShow() {
        RefundService policy = new RefundService();
        assertEquals(100, policy.refundPercent(TODAY, TODAY.plusDays(14)));
        assertEquals(50, policy.refundPercent(TODAY, TODAY.plusDays(13)));
        assertEquals(50, policy.refundPercent(TODAY, TODAY.plusDays(3)));
        assertEquals(0, policy.refundPercent(TODAY, TODAY.plusDays(2)));
    }

    @Test
    void passesGatesAndRefundsSurviveARestart() {
        AccessPass pass = access.grant("REF-1", "alice", "FLOOR", 9_000);
        access.closeGate("G2");
        access.refund(pass.id());
        access.configureGate("G2", List.of("BALCONY"), 99);

        AccessService afterRestart = accessFor(dataDir, TODAY.plusDays(20));

        assertEquals(pass.id(), afterRestart.findByReference("REF-1").orElseThrow().id());
        AccessControlGate.Gate g2 = afterRestart.gates().stream().filter(g -> g.id().equals("G2")).findFirst().orElseThrow();
        assertFalse(g2.open());
        assertEquals(1, g2.capacity());
        assertEquals(1, afterRestart.refunds().size());
    }

    @Test
    void unknownIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> access.closeGate("G9"));
        assertThrows(IllegalArgumentException.class, () -> access.scan("P-nope"));
    }
}
