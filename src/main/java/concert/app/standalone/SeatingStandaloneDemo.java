package concert.app.standalone;

import concert.seating.DynamicPricingEngine;
import concert.seating.SeatHold;
import concert.seating.SeatHoldRepository;
import concert.seating.SeatUnavailableException;
import concert.seating.SeatingChart;
import concert.seating.SeatingService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

public final class SeatingStandaloneDemo {
    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : "data-standalone");
        SeatingService seating = new SeatingService(new SeatHoldRepository(dir), new SeatingChart(),
                new DynamicPricingEngine(), LocalDate.now().plusDays(2), Clock.systemDefaultZone());

        String seat = seating.availableSeats(SeatingChart.Section.VIP).get(0);
        System.out.println("Quote for " + seat + " (2 days before the show): " + seating.quoteCents(seat) + "c");
        SeatHold hold = seating.hold(seat, "standalone-fan", "STANDALONE");
        System.out.println("Held:      " + hold);
        try {
            seating.hold(seat, "someone-else", "STANDALONE-2");
        } catch (SeatUnavailableException e) {
            System.out.println("Second hold refused: " + e.getMessage());
        }
        System.out.println("Confirmed: " + seating.confirm(hold.id()));
        System.out.println("Released:  " + seating.release(hold.id(), "demo finished"));
        System.out.println("Holds on disk in " + dir.toAbsolutePath() + ": " + seating.allHolds().size());
    }
}
