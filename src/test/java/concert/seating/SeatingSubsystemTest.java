package concert.seating;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class SeatingSubsystemTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dataDir;

    private SeatingService seating;

    @BeforeEach
    void freshVenue() {
        seating = seatingFor(dataDir, TODAY.plusDays(20));
    }

    private static SeatingService seatingFor(Path dir, LocalDate eventDate) {
        return new SeatingService(new SeatHoldRepository(dir), new SeatingChart(),
                new DynamicPricingEngine(), eventDate, CLOCK);
    }

    @Test
    void holdingASeatPersistsItAsHeldAtTheQuotedPrice() {
        long quote = seating.quoteCents("C7");

        SeatHold hold = seating.hold("c7", "alice", "REF-1");

        assertEquals("C7", hold.seatCode());
        assertEquals(SeatingChart.Section.FLOOR, hold.section());
        assertEquals(SeatHold.Status.HELD, hold.status());
        assertEquals(quote, hold.priceCents());
        assertEquals(SeatHold.Status.HELD, new SeatHoldRepository(dataDir).findById(hold.id()).orElseThrow().status());
    }

    @Test
    void aSeatCannotBeHeldTwice() {
        seating.hold("A1", "alice", "REF-1");

        assertThrows(SeatUnavailableException.class, () -> seating.hold("A1", "bob", "REF-2"));
        assertFalse(seating.isAvailable("A1"));
    }

    @Test
    void unknownSeatsAreRejectedAsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> seating.hold("Z99", "alice", "REF-1"));
        assertThrows(IllegalArgumentException.class, () -> seating.hold("C11", "alice", "REF-1"));
        assertThrows(IllegalArgumentException.class, () -> seating.hold(null, "alice", "REF-1"));
    }

    @Test
    void releasingFreesTheSeatAndIsIdempotent() {
        SeatHold hold = seating.hold("G3", "alice", "REF-1");

        seating.release(hold.id(), "test");
        SeatHold again = seating.release(hold.id(), "test twice");

        assertEquals(SeatHold.Status.RELEASED, again.status());
        assertEquals("test", again.releaseReason());
        assertTrue(seating.isAvailable("G3"));
    }

    @Test
    void confirmedSeatsCanStillBeReleasedButReleasedOnesCannotBeConfirmed() {
        SeatHold hold = seating.hold("B2", "alice", "REF-1");
        assertEquals(SeatHold.Status.CONFIRMED, seating.confirm(hold.id()).status());
        assertEquals(SeatHold.Status.CONFIRMED, seating.confirm(hold.id()).status());

        seating.release(hold.id(), "refund");

        assertThrows(IllegalStateException.class, () -> seating.confirm(hold.id()));
    }

    @Test
    void statePersistsAcrossARestart() {
        SeatHold hold = seating.hold("H12", "alice", "REF-9");

        SeatingService afterRestart = seatingFor(dataDir, TODAY.plusDays(20));

        assertEquals(hold.id(), afterRestart.findByReference("REF-9").orElseThrow().id());
        assertFalse(afterRestart.isAvailable("H12"));
        assertFalse(afterRestart.availableSeats(SeatingChart.Section.BALCONY).contains("H12"));
        assertEquals(1, afterRestart.allHolds().size());
    }

    @Test
    void unknownHoldIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> seating.release("H-nope", "x"));
        assertTrue(seating.find("H-nope").isEmpty());
    }

    @Test
    void chartKnowsSectionsAndCapacities() {
        SeatingChart chart = new SeatingChart();
        assertEquals(16, SeatingChart.Section.VIP.capacity());
        assertEquals(40, chart.seatsIn(SeatingChart.Section.FLOOR).size());
        assertEquals(SeatingChart.Section.BALCONY, chart.sectionOf(" g1 "));
        assertFalse(chart.exists("A9"));
        assertFalse(chart.exists("1A"));
    }

    @Test
    void priceRisesWithDemandAndProximity() {
        DynamicPricingEngine engine = new DynamicPricingEngine();
        SeatingChart.Section floor = SeatingChart.Section.FLOOR;

        assertEquals(9_000, engine.priceCents(floor, 0, 20));
        assertEquals(11_250, engine.priceCents(floor, 20, 20));
        assertEquals(13_500, engine.priceCents(floor, 32, 20));
        assertEquals(10_800, engine.priceCents(floor, 0, 2));
        assertEquals(8_100, engine.priceCents(floor, 0, 90));
    }

    @Test
    void quoteUsesPersistedOccupancy() {
        SeatingService lastMinute = seatingFor(dataDir, TODAY.plusDays(1));
        assertEquals(5_400, lastMinute.quoteCents("G1"));
    }
}
