package concert.seating;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeatingSubsystemTest {
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
}
