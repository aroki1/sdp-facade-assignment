package concert.access;

import concert.storage.JsonFileStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Persistence gateway: access/passes.json, gates.json, refunds.json. */
public class AccessRepository {
    private final JsonFileStore<AccessPass> passes;
    private final JsonFileStore<AccessControlGate.Gate> gates;
    private final JsonFileStore<RefundService.Refund> refunds;

    public AccessRepository(Path dataDir) {
        Path dir = dataDir.resolve("access");
        this.passes = new JsonFileStore<>(dir.resolve("passes.json"), AccessPass.class);
        this.gates = new JsonFileStore<>(dir.resolve("gates.json"), AccessControlGate.Gate.class);
        this.refunds = new JsonFileStore<>(dir.resolve("refunds.json"), RefundService.Refund.class);
    }

    public void savePass(AccessPass pass) {
        passes.upsert(pass, AccessPass::id);
    }

    public Optional<AccessPass> findPass(String id) {
        return passes.readAll().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public Optional<AccessPass> findPassByReference(String reference) {
        return passes.readAll().stream().filter(p -> reference.equals(p.reference())).reduce((first, latest) -> latest);
    }

    public int countPassesAt(String gateId) {
        return (int) passes.readAll().stream()
                .filter(p -> p.gateId().equals(gateId) && p.countsAgainstCapacity())
                .count();
    }

    public List<AccessPass> allPasses() {
        return passes.readAll();
    }

    public void saveGate(AccessControlGate.Gate gate) {
        gates.upsert(gate, AccessControlGate.Gate::id);
    }

    public Optional<AccessControlGate.Gate> findGate(String id) {
        return gates.readAll().stream().filter(g -> g.id().equals(id)).findFirst();
    }

    public List<AccessControlGate.Gate> allGates() {
        return gates.readAll();
    }

    public void saveRefund(RefundService.Refund refund) {
        List<RefundService.Refund> all = refunds.readAll();
        all.add(refund);
        refunds.writeAll(all);
    }

    public Optional<RefundService.Refund> findRefundForPass(String passId) {
        return refunds.readAll().stream().filter(r -> r.passId().equals(passId)).findFirst();
    }

    public List<RefundService.Refund> allRefunds() {
        return refunds.readAll();
    }
}
