package concert.ticketing;

import concert.storage.JsonFileStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Persistence gateway: ticketing/tickets.json and blocklist.json. */
public class TicketRepository {
    private final JsonFileStore<Ticket> tickets;
    private final JsonFileStore<String> blocklist;

    public TicketRepository(Path dataDir) {
        Path dir = dataDir.resolve("ticketing");
        this.tickets = new JsonFileStore<>(dir.resolve("tickets.json"), Ticket.class);
        this.blocklist = new JsonFileStore<>(dir.resolve("blocklist.json"), String.class);
    }

    public void save(Ticket ticket) {
        tickets.upsert(ticket, Ticket::id);
    }

    public Optional<Ticket> findById(String id) {
        return tickets.readAll().stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public Optional<Ticket> findByReference(String reference) {
        return tickets.readAll().stream().filter(t -> reference.equals(t.reference())).reduce((first, latest) -> latest);
    }

    public int countLiveFor(String holderId) {
        return (int) tickets.readAll().stream()
                .filter(t -> t.holderId().equals(holderId) && t.isLive())
                .count();
    }

    public List<Ticket> findAll() {
        return tickets.readAll();
    }

    public boolean isBlocklisted(String buyerId) {
        return blocklist.readAll().contains(buyerId);
    }

    public void setBlocklisted(String buyerId, boolean blocked) {
        List<String> ids = blocklist.readAll();
        ids.remove(buyerId);
        if (blocked) {
            ids.add(buyerId);
        }
        blocklist.writeAll(ids);
    }

    public List<String> blocklist() {
        return blocklist.readAll();
    }
}
