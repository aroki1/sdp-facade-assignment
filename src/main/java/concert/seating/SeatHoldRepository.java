package concert.seating;

import concert.storage.JsonFileStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Persistence gateway: seating/holds.json. */
public class SeatHoldRepository {
    private final JsonFileStore<SeatHold> store;

    public SeatHoldRepository(Path dataDir) {
        this.store = new JsonFileStore<>(dataDir.resolve("seating").resolve("holds.json"), SeatHold.class);
    }

    public void save(SeatHold hold) {
        store.upsert(hold, SeatHold::id);
    }

    public Optional<SeatHold> findById(String id) {
        return store.readAll().stream().filter(h -> h.id().equals(id)).findFirst();
    }

    public Optional<SeatHold> findByReference(String reference) {
        return store.readAll().stream().filter(h -> reference.equals(h.reference())).reduce((first, latest) -> latest);
    }

    public Optional<SeatHold> findActiveBySeat(String seatCode) {
        return store.readAll().stream()
                .filter(h -> h.seatCode().equals(seatCode) && h.isActive())
                .findFirst();
    }

    public int countActiveIn(SeatingChart.Section section) {
        return (int) store.readAll().stream()
                .filter(h -> h.section() == section && h.isActive())
                .count();
    }

    public List<SeatHold> findAll() {
        return store.readAll();
    }
}
