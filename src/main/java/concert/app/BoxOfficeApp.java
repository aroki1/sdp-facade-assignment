package concert.app;

import concert.facade.BoxOffice;
import concert.facade.BoxOfficeAdminFacade;
import concert.facade.ChainStatus;
import concert.facade.ChainStep;
import concert.facade.InvalidPurchaseException;
import concert.facade.OrderRecord;
import concert.facade.PurchaseFailedException;
import concert.facade.PurchaseReceipt;
import concert.facade.PurchaseRequest;
import concert.facade.TicketPurchaseFacade;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public final class BoxOfficeApp {
    private static final String EVENT_NAME = "Aurora Live - Winter Tour";

    private final Scanner in = new Scanner(System.in);
    private final Path dataDir;
    private final LocalDate eventDate;
    private final TicketPurchaseFacade purchases;
    private final BoxOfficeAdminFacade admin;
    private volatile ChainStatus crashArmedAt;

    private BoxOfficeApp(Path dataDir) {
        this.dataDir = dataDir;
        this.eventDate = eventDate(dataDir);
        BoxOffice office = BoxOffice.open(dataDir, eventDate, Clock.systemDefaultZone(), this::onCheckpoint);
        this.purchases = office.purchases();
        this.admin = office.admin();
    }

    public static void main(String[] args) {
        Path dataDir = Paths.get(args.length > 0 ? args[0] : "data").toAbsolutePath();
        new BoxOfficeApp(dataDir).run();
    }

    private void onCheckpoint(OrderRecord order) {
        if (order.status() == crashArmedAt) {
            System.out.println();
            System.out.println("  !!! SIMULATED CRASH: order " + order.orderId() + " was just persisted as "
                    + order.status() + ". Halting the JVM now (no shutdown hooks, no cleanup).");
            System.out.println("  !!! Restart the app and choose 11 to see it recover from disk.");
            System.out.flush();
            Runtime.getRuntime().halt(137);
        }
    }

    private void run() {
        System.out.println("==============================================================");
        System.out.println("  CONCERT BOX OFFICE  -  " + EVENT_NAME + "  -  " + eventDate);
        System.out.println("  data directory: " + dataDir);
        System.out.println("==============================================================");
        List<OrderRecord> interrupted = admin.interruptedOrders();
        if (!interrupted.isEmpty()) {
            System.out.println("  Found " + interrupted.size() + " interrupted order(s) on disk from a previous run:");
            interrupted.forEach(o -> System.out.println("    " + o.orderId() + "  " + o.status() + "  seat " + o.seatCode()));
            System.out.println("  -> choose 11 to resume them, or 12 to compensate one manually.");
        }
        while (true) {
            printMenu();
            String choice = ask("Choice").toUpperCase();
            try {
                switch (choice) {
                    case "1" -> showSeats();
                    case "2" -> buy();
                    case "3" -> listOrders();
                    case "4" -> inspect(ask("Order id"));
                    case "5" -> System.out.println(admin.printTicket(ask("Order id")));
                    case "6" -> System.out.println("  Admitted: " + admin.admit(ask("Order id")));
                    case "7" -> cancel();
                    case "8" -> blocklist();
                    case "9" -> gates();
                    case "10" -> armCrash();
                    case "11" -> resume();
                    case "12" -> compensateManually();
                    case "S1" -> scenarioTicketingFails();
                    case "S2" -> scenarioAccessFails();
                    case "0", "Q" -> {
                        System.out.println("Bye. Everything is already on disk in " + dataDir);
                        return;
                    }
                    default -> System.out.println("  Unknown choice.");
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("--- Fan -------------------------------------------------------");
        System.out.println("  1  Seat map and live prices          2  Buy a ticket (A -> B -> C)");
        System.out.println("--- Inspect ---------------------------------------------------");
        System.out.println("  3  List orders (from disk)           4  Inspect order in all subsystems");
        System.out.println("  5  Print ticket                      6  Admit fan at the gate (scan)");
        System.out.println("--- Admin / failure injection ---------------------------------");
        System.out.println("  7  Cancel completed order + refund   8  Blocklist buyer  (makes B fail)");
        System.out.println("  9  Gates: close/open/capacity  (makes C fail)");
        System.out.println(" 10  Arm a crash after a step is persisted (kills the JVM)" + armedLabel());
        System.out.println(" 11  Resume interrupted orders         12  Manually compensate a stuck order");
        System.out.println("--- One-key scenarios -----------------------------------------");
        System.out.println(" S1  B fails after A  -> undo A        S2  C fails after A,B -> undo B then A");
        System.out.println("  0  Exit");
    }

    private String armedLabel() {
        return crashArmedAt == null ? "" : "   [ARMED at " + crashArmedAt + "]";
    }

    private void showSeats() {
        for (Map.Entry<String, Map<String, Long>> section : purchases.seatOffers().entrySet()) {
            Map<String, Long> free = section.getValue();
            String price = free.isEmpty() ? "SOLD OUT" : money(free.values().iterator().next());
            System.out.printf("  %-8s %-9s %2d free: %s%n", section.getKey(), price, free.size(),
                    String.join(" ", free.keySet()));
        }
    }

    private void buy() {
        showSeats();
        String seat = ask("Seat (e.g. C7)");
        String buyer = ask("Buyer id");
        String email = ask("E-mail");
        purchase(new PurchaseRequest(buyer, email, seat));
    }

    private String purchase(PurchaseRequest request) {
        try {
            PurchaseReceipt r = purchases.purchase(request);
            System.out.println("  OK  order " + r.orderId() + " COMPLETED");
            System.out.println("      seat " + r.seatCode() + " (" + r.zone() + ") for " + money(r.priceCents())
                    + ", ticket " + r.ticketId() + " barcode " + r.barcode() + ", pass " + r.passId()
                    + " via gate " + r.gateId());
            return r.orderId();
        } catch (InvalidPurchaseException e) {
            System.out.println("  INVALID REQUEST (nothing recorded): " + e.getMessage());
            return null;
        } catch (PurchaseFailedException e) {
            System.out.println("  FAILED  " + e.getMessage());
            describeFailure(e);
            return e.orderId();
        }
    }

    private void describeFailure(PurchaseFailedException e) {
        if (e.failedStep() == ChainStep.SEATING) {
            System.out.println("      step A failed first, so nothing had been committed: order is REJECTED.");
        } else {
            System.out.println("      step " + e.failedStep() + " failed after earlier steps were committed;");
            System.out.println("      the facade undid " + e.compensatedSteps() + " (newest first). See option 4.");
        }
    }

    private void listOrders() {
        List<OrderRecord> orders = admin.orders();
        if (orders.isEmpty()) {
            System.out.println("  No orders yet.");
        }
        for (OrderRecord o : orders) {
            System.out.printf("  %-10s %-13s seat %-4s buyer %-10s %8s%s%n", o.orderId(), o.status(), o.seatCode(),
                    o.buyerId(), o.priceCents() == 0 ? "-" : money(o.priceCents()),
                    o.failedStep() == null ? "" : "  failed at " + o.failedStep() + ", undone " + o.compensatedSteps());
        }
    }

    private void inspect(String orderId) {
        BoxOfficeAdminFacade.OrderInspection view = admin.inspect(orderId);
        OrderRecord o = view.order();
        System.out.println("  Journal (orders/orders.json): " + o.orderId() + " status " + o.status());
        if (o.failureReason() != null) {
            System.out.println("    failed at " + o.failedStep() + ": " + o.failureReason());
        }
        o.history().forEach(h -> System.out.println("      " + h));
        System.out.println("  A  seating/holds.json    : " + view.seatHold());
        System.out.println("  B  ticketing/tickets.json: " + view.ticket());
        System.out.println("  C  access/passes.json    : " + view.accessPass());
        System.out.println("  C  access/refunds.json   : " + view.refund());
    }

    private void cancel() {
        BoxOfficeAdminFacade.Cancellation c = admin.cancelCompletedOrder(ask("Completed order id"));
        System.out.println("  Order " + c.orderId() + " CANCELLED. Refunded " + money(c.refundCents())
                + " (" + c.refundPercent() + "% under the refund policy). Pass revoked, ticket voided, seat released.");
    }

    private void blocklist() {
        System.out.println("  Blocklist: " + admin.blocklist());
        String buyer = ask("Buyer id to toggle");
        if (admin.blocklist().contains(buyer)) {
            admin.unblockBuyer(buyer);
            System.out.println("  " + buyer + " removed from the blocklist.");
        } else {
            admin.blockBuyer(buyer);
            System.out.println("  " + buyer + " blocklisted. Their next purchase will fail at step B (fraud check).");
        }
    }

    private void gates() {
        printGates();
        String gate = ask("Gate id").toUpperCase();
        String action = ask("[c]lose, [o]pen or [s]et capacity").toLowerCase();
        switch (action) {
            case "c" -> admin.closeGate(gate);
            case "o" -> admin.openGate(gate);
            case "s" -> admin.setGateCapacity(gate, Integer.parseInt(ask("New capacity")));
            default -> System.out.println("  Nothing changed.");
        }
        printGates();
    }

    private void printGates() {
        for (BoxOfficeAdminFacade.GateStatus g : admin.gates()) {
            System.out.printf("  %-3s %-7s zones %-15s passes %d/%d%n", g.gateId(), g.open() ? "OPEN" : "CLOSED",
                    g.zones(), g.passes(), g.capacity());
        }
    }

    private void armCrash() {
        System.out.println("  Crash right after the order is persisted as:");
        System.out.println("    1  PENDING_B     (seat held, nothing else)");
        System.out.println("    2  PENDING_C     (seat held + ticket issued)");
        System.out.println("    3  COMPENSATING  (a step failed, undo not yet done)");
        System.out.println("    0  disarm");
        crashArmedAt = switch (ask("Choice")) {
            case "1" -> ChainStatus.PENDING_B;
            case "2" -> ChainStatus.PENDING_C;
            case "3" -> ChainStatus.COMPENSATING;
            default -> null;
        };
        System.out.println(crashArmedAt == null ? "  Disarmed."
                : "  Armed. The next purchase that reaches " + crashArmedAt + " kills the app.");
    }

    private void resume() {
        List<TicketPurchaseFacade.ResumeOutcome> outcomes = purchases.resumeInterrupted();
        if (outcomes.isEmpty()) {
            System.out.println("  Nothing to resume: no order on disk is unfinished.");
        }
        for (TicketPurchaseFacade.ResumeOutcome r : outcomes) {
            System.out.println("  " + r.orderId() + ": " + r.statusBefore() + " -> " + r.statusAfter() + "  (" + r.detail() + ")");
        }
    }

    private void compensateManually() {
        admin.interruptedOrders().forEach(o -> System.out.println("  " + o.orderId() + "  " + o.status()));
        String orderId = ask("Order id");
        List<ChainStep> undone = admin.compensateManually(orderId, ask("Reason"));
        System.out.println("  Undone (newest first): " + undone);
    }

    private void scenarioTicketingFails() {
        String seat = firstFree("FLOOR");
        System.out.println("  Scenario S1: 'mallory' is on the fraud blocklist and tries to buy " + seat + ".");
        admin.blockBuyer("mallory");
        String orderId = purchase(new PurchaseRequest("mallory", "mallory@example.com", seat));
        inspect(orderId);
        System.out.println("  -> seat " + seat + " is free again: " + purchases.seatOffers().get("FLOOR").containsKey(seat));
    }

    private void scenarioAccessFails() {
        String seat = firstFree("BALCONY");
        System.out.println("  Scenario S2: gate " + BoxOffice.BALCONY_GATE + " is closed (safety inspection);"
                + " 'bob' tries to buy balcony seat " + seat + ".");
        admin.closeGate(BoxOffice.BALCONY_GATE);
        try {
            String orderId = purchase(new PurchaseRequest("bob", "bob@example.com", seat));
            inspect(orderId);
            System.out.println("  -> seat " + seat + " is free again: "
                    + purchases.seatOffers().get("BALCONY").containsKey(seat));
        } finally {
            admin.openGate(BoxOffice.BALCONY_GATE);
            System.out.println("  (gate " + BoxOffice.BALCONY_GATE + " re-opened)");
        }
    }

    private String firstFree(String section) {
        return purchases.seatOffers().get(section).keySet().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(section + " is sold out"));
    }

    private String ask(String prompt) {
        System.out.print("  " + prompt + ": ");
        System.out.flush();
        if (!in.hasNextLine()) {
            System.exit(0);
        }
        return in.nextLine().trim();
    }

    private static String money(long cents) {
        return String.format("$%d.%02d", cents / 100, cents % 100);
    }

    private static LocalDate eventDate(Path dataDir) {
        Path file = dataDir.resolve("event.properties");
        Properties props = new Properties();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    props.load(r);
                }
                return LocalDate.parse(props.getProperty("eventDate"));
            }
            LocalDate date = LocalDate.now().plusDays(20);
            props.setProperty("eventDate", date.toString());
            Files.createDirectories(dataDir);
            try (Writer w = Files.newBufferedWriter(file)) {
                props.store(w, EVENT_NAME);
            }
            return date;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
