package concert.facade;

import concert.storage.JsonFileStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persists chain progress: orders/orders.json. */
public class OrderJournal {
    private final JsonFileStore<OrderRecord> store;

    public OrderJournal(Path dataDir) {
        this.store = new JsonFileStore<>(dataDir.resolve("orders").resolve("orders.json"), OrderRecord.class);
    }

    OrderRecord open(String buyerId, String email, String seatCode) {
        OrderRecord order = new OrderRecord("ORD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                buyerId, email, seatCode);
        save(order);
        return order;
    }

    void save(OrderRecord order) {
        store.upsert(order, OrderRecord::orderId);
    }

    public Optional<OrderRecord> find(String orderId) {
        return store.readAll().stream().filter(o -> o.orderId().equalsIgnoreCase(orderId)).findFirst();
    }

    public List<OrderRecord> all() {
        return store.readAll();
    }

    public List<OrderRecord> unfinished() {
        return store.readAll().stream().filter(o -> o.status().isUnfinished()).toList();
    }

    OrderRecord require(String orderId) {
        return find(orderId).orElseThrow(() -> new IllegalArgumentException("Unknown order " + orderId));
    }
}
