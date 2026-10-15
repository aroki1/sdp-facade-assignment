package concert.app.standalone;

import concert.ticketing.FraudChecker;
import concert.ticketing.FraudRejectedException;
import concert.ticketing.Ticket;
import concert.ticketing.TicketPrinter;
import concert.ticketing.TicketRepository;
import concert.ticketing.TicketingService;

import java.nio.file.Path;

public final class TicketingStandaloneDemo {
    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : "data-standalone");
        TicketingService ticketing = new TicketingService(new TicketRepository(dir), new FraudChecker(),
                new TicketPrinter("standalone-key"));

        Ticket ticket = ticketing.issue("STANDALONE", "standalone-fan", "fan@example.com", "General admission", 2_500);
        System.out.println(ticketing.print(ticket.id()));
        System.out.println("Genuine barcode: " + ticketing.isGenuine(ticket.id()));

        ticketing.blockBuyer("scalper-bot");
        try {
            ticketing.issue("STANDALONE-2", "scalper-bot", "bot@example.com", "General admission", 2_500);
        } catch (FraudRejectedException e) {
            System.out.println(e.getMessage());
        }
        ticketing.unblockBuyer("scalper-bot");
        System.out.println("Voided: " + ticketing.voidTicket(ticket.id(), "demo finished"));
    }
}
