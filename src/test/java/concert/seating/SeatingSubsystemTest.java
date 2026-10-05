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
}
