package concert.access;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Subsystem C entry point. */
public class AccessService {
    private final AccessRepository repository;
    private final AccessControlGate gateRules;
    private final RefundService refundPolicy;
    private final LocalDate eventDate;
    private final Clock clock;

    public AccessService(AccessRepository repository, AccessControlGate gateRules, RefundService refundPolicy,
                         LocalDate eventDate, Clock clock) {
        this.repository = repository;
        this.gateRules = gateRules;
        this.refundPolicy = refundPolicy;
        this.eventDate = eventDate;
        this.clock = clock;
    }

    public void configureGate(String gateId, List<String> zones, int capacity) {
        if (repository.findGate(gateId).isEmpty()) {
            repository.saveGate(new AccessControlGate.Gate(gateId, List.copyOf(zones), capacity, true));
        }
    }

    public void openGate(String gateId) {
        repository.saveGate(requireGate(gateId).withOpen(true));
    }

    public void closeGate(String gateId) {
        repository.saveGate(requireGate(gateId).withOpen(false));
    }

    public void setCapacity(String gateId, int capacity) {
        repository.saveGate(requireGate(gateId).withCapacity(capacity));
    }

    public List<AccessControlGate.Gate> gates() {
        return repository.allGates();
    }

    public int passesAt(String gateId) {
        return repository.countPassesAt(gateId);
    }

    public AccessPass grant(String reference, String holderId, String zone, long paidCents) {
        AccessControlGate.Gate gate = gateRules.gateFor(zone, repository.allGates());
        gateRules.checkAdmission(gate, repository.countPassesAt(gate.id()));
        AccessPass pass = new AccessPass("P-" + UUID.randomUUID().toString().substring(0, 8),
                reference, holderId, zone, gate.id(), paidCents);
        repository.savePass(pass);
        return pass;
    }

    public AccessPass scan(String passId) {
        AccessPass pass = requirePass(passId);
        gateRules.checkScan(pass, requireGate(pass.gateId()));
        pass.markUsed();
        repository.savePass(pass);
        return pass;
    }

    public AccessPass revoke(String passId, String reason) {
        AccessPass pass = requirePass(passId);
        if (pass.status() != AccessPass.Status.REVOKED) {
            pass.revoke(reason);
            repository.savePass(pass);
        }
        return pass;
    }

    public RefundService.Refund refund(String passId) {
        Optional<RefundService.Refund> existing = repository.findRefundForPass(passId);
        if (existing.isPresent()) {
            return existing.get();
        }
        AccessPass pass = requirePass(passId);
        RefundService.Refund refund = refundPolicy.computeRefund(pass, LocalDate.now(clock), eventDate);
        revoke(passId, "refunded " + refund.percent() + "%");
        repository.saveRefund(refund);
        return refund;
    }

    public Optional<AccessPass> find(String passId) {
        return repository.findPass(passId);
    }

    public Optional<AccessPass> findByReference(String reference) {
        return repository.findPassByReference(reference);
    }

    public List<AccessPass> allPasses() {
        return repository.allPasses();
    }

    public List<RefundService.Refund> refunds() {
        return repository.allRefunds();
    }

    private AccessPass requirePass(String passId) {
        return repository.findPass(passId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown access pass " + passId));
    }

    private AccessControlGate.Gate requireGate(String gateId) {
        return repository.findGate(gateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown gate " + gateId));
    }
}
