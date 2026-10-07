package concert.seating;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Subsystem A entry point. */
public class SeatingService {
    private final SeatHoldRepository repository;
    private final SeatingChart chart;
    private final DynamicPricingEngine pricing;
    private final LocalDate eventDate;
    private final Clock clock;

    public SeatingService(SeatHoldRepository repository, SeatingChart chart, DynamicPricingEngine pricing,
                          LocalDate eventDate, Clock clock) {
        this.repository = repository;
        this.chart = chart;
        this.pricing = pricing;
        this.eventDate = eventDate;
        this.clock = clock;
    }

    public long quoteCents(String seatCode) {
        SeatingChart.Section section = chart.sectionOf(seatCode);
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), eventDate);
        return pricing.priceCents(section, repository.countActiveIn(section), days);
    }

    public SeatHold hold(String seatCode, String holderId, String reference) {
        String code = chart.normalise(seatCode);
        SeatingChart.Section section = chart.sectionOf(code);
        repository.findActiveBySeat(code).ifPresent(existing -> {
            throw new SeatUnavailableException("Seat " + code + " is already " + existing.status());
        });
        long price = quoteCents(code);
        SeatHold hold = new SeatHold("H-" + shortId(), reference, code, section, holderId, price);
        repository.save(hold);
        return hold;
    }

    public SeatHold confirm(String holdId) {
        SeatHold hold = require(holdId);
        if (hold.status() != SeatHold.Status.CONFIRMED) {
            hold.confirm();
            repository.save(hold);
        }
        return hold;
    }

    public SeatHold release(String holdId, String reason) {
        SeatHold hold = require(holdId);
        if (hold.status() != SeatHold.Status.RELEASED) {
            hold.release(reason);
            repository.save(hold);
        }
        return hold;
    }

    public Optional<SeatHold> find(String holdId) {
        return repository.findById(holdId);
    }

    public Optional<SeatHold> findByReference(String reference) {
        return repository.findByReference(reference);
    }

    public SeatingChart.Section sectionOf(String seatCode) {
        return chart.sectionOf(seatCode);
    }

    public boolean isAvailable(String seatCode) {
        return chart.exists(seatCode) && repository.findActiveBySeat(chart.normalise(seatCode)).isEmpty();
    }

    public List<String> availableSeats(SeatingChart.Section section) {
        return chart.seatsIn(section).stream().filter(this::isAvailable).toList();
    }

    public List<SeatHold> allHolds() {
        return repository.findAll();
    }

    private SeatHold require(String holdId) {
        return repository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown seat hold " + holdId));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
