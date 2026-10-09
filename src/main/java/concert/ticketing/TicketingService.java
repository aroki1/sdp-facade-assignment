package concert.ticketing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Subsystem B entry point. */
public class TicketingService {
    private final TicketRepository repository;
    private final FraudChecker fraudChecker;
    private final TicketPrinter printer;

    public TicketingService(TicketRepository repository, FraudChecker fraudChecker, TicketPrinter printer) {
        this.repository = repository;
        this.fraudChecker = fraudChecker;
        this.printer = printer;
    }

    public Ticket issue(String reference, String holderId, String email, String description, long priceCents) {
        FraudChecker.Verdict verdict = fraudChecker.assess(holderId, email,
                repository.isBlocklisted(holderId), repository.countLiveFor(holderId));
        if (!verdict.approved()) {
            throw new FraudRejectedException(verdict.reason());
        }
        Ticket ticket = new Ticket("T-" + UUID.randomUUID().toString().substring(0, 8),
                reference, holderId, email, description, priceCents);
        ticket.stampBarcode(printer.barcodeFor(ticket.id(), holderId));
        repository.save(ticket);
        return ticket;
    }

    public Ticket activate(String ticketId) {
        Ticket ticket = require(ticketId);
        if (ticket.status() != Ticket.Status.ACTIVATED) {
            ticket.activate();
            repository.save(ticket);
        }
        return ticket;
    }

    public Ticket voidTicket(String ticketId, String reason) {
        Ticket ticket = require(ticketId);
        if (ticket.status() != Ticket.Status.VOIDED) {
            ticket.voidTicket(reason);
            repository.save(ticket);
        }
        return ticket;
    }

    public String print(String ticketId) {
        return printer.render(require(ticketId));
    }

    public boolean isGenuine(String ticketId) {
        return printer.isGenuine(require(ticketId));
    }

    public void blockBuyer(String buyerId) {
        repository.setBlocklisted(buyerId, true);
    }

    public void unblockBuyer(String buyerId) {
        repository.setBlocklisted(buyerId, false);
    }

    public List<String> blocklist() {
        return repository.blocklist();
    }

    public Optional<Ticket> find(String ticketId) {
        return repository.findById(ticketId);
    }

    public Optional<Ticket> findByReference(String reference) {
        return repository.findByReference(reference);
    }

    public List<Ticket> allTickets() {
        return repository.findAll();
    }

    private Ticket require(String ticketId) {
        return repository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ticket " + ticketId));
    }
}
