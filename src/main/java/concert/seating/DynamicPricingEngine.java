package concert.seating;

/** Business logic: price by section, demand and days to the show. */
public class DynamicPricingEngine {
    public long priceCents(SeatingChart.Section section, int soldInSection, long daysUntilEvent) {
        double occupancy = (double) soldInSection / section.capacity();
        double demand = occupancy >= 0.8 ? 1.50
                : occupancy >= 0.5 ? 1.25
                : 1.00;
        double timing = daysUntilEvent <= 3 ? 1.20
                : daysUntilEvent >= 60 ? 0.90
                : 1.00;
        return Math.round(section.basePriceCents() * demand * timing);
    }
}
