package concert.facade;

import concert.access.AccessControlGate;
import concert.access.AccessRepository;
import concert.access.AccessService;
import concert.seating.DynamicPricingEngine;
import concert.seating.SeatHoldRepository;
import concert.seating.SeatingChart;
import concert.seating.SeatingService;
import concert.ticketing.FraudChecker;
import concert.ticketing.TicketPrinter;
import concert.ticketing.TicketRepository;
import concert.ticketing.TicketingService;
import concert.access.RefundService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Composition root: wires subsystems and facades over one data dir. */
public final class BoxOffice {
    public static final String MAIN_GATE = "G1";
    public static final String BALCONY_GATE = "G2";

    private final TicketPurchaseFacade purchases;
    private final BoxOfficeAdminFacade admin;

    private BoxOffice(TicketPurchaseFacade purchases, BoxOfficeAdminFacade admin) {
        this.purchases = purchases;
        this.admin = admin;
    }

    public static BoxOffice open(Path dataDir, LocalDate eventDate, Clock clock, ChainCheckpointListener listener) {
        SeatingService seating = new SeatingService(new SeatHoldRepository(dataDir), new SeatingChart(),
                new DynamicPricingEngine(), eventDate, clock);
        TicketingService ticketing = new TicketingService(new TicketRepository(dataDir), new FraudChecker(),
                new TicketPrinter("concert-box-office-signing-key"));
        AccessService access = new AccessService(new AccessRepository(dataDir), new AccessControlGate(),
                new RefundService(), eventDate, clock);
        access.configureGate(MAIN_GATE, List.of(SeatingChart.Section.VIP.name(), SeatingChart.Section.FLOOR.name()), 56);
        access.configureGate(BALCONY_GATE, List.of(SeatingChart.Section.BALCONY.name()), 24);

        OrderJournal journal = new OrderJournal(dataDir);
        return new BoxOffice(
                new TicketPurchaseFacade(seating, ticketing, access, journal, listener),
                new BoxOfficeAdminFacade(seating, ticketing, access, journal, listener));
    }

    public TicketPurchaseFacade purchases() {
        return purchases;
    }

    public BoxOfficeAdminFacade admin() {
        return admin;
    }
}
