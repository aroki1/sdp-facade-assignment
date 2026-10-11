package concert.facade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OrderRecord {
    private String orderId;
    private String buyerId;
    private String email;
    private String seatCode;
    private String zone;
    private ChainStatus status;
    private String holdId;
    private String ticketId;
    private String passId;
    private String gateId;
    private long priceCents;
    private ChainStep failedStep;
    private String failureReason;
    private List<ChainStep> compensatedSteps = new ArrayList<>();
    private Long refundCents;
    private List<String> history = new ArrayList<>();

    private OrderRecord() {
    }

    OrderRecord(String orderId, String buyerId, String email, String seatCode) {
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.email = email;
        this.seatCode = seatCode;
        this.status = ChainStatus.PENDING_A;
        log("order opened for seat " + seatCode);
    }

    void seatHeld(String holdId, long priceCents, String zone) {
        this.holdId = holdId;
        this.priceCents = priceCents;
        this.zone = zone;
        moveTo(ChainStatus.PENDING_B, "A committed: seat hold " + holdId);
    }

    void ticketIssued(String ticketId) {
        this.ticketId = ticketId;
        moveTo(ChainStatus.PENDING_C, "B committed: ticket " + ticketId);
    }

    void completed(String passId, String gateId) {
        this.passId = passId;
        this.gateId = gateId;
        moveTo(ChainStatus.COMPLETED, "C committed: pass " + passId + " at gate " + gateId);
    }

    void rejected(ChainStep step, String reason) {
        this.failedStep = step;
        this.failureReason = reason;
        moveTo(ChainStatus.REJECTED, step + " failed before anything was committed: " + reason);
    }

    void compensating(ChainStep step, String reason) {
        if (status != ChainStatus.COMPENSATING) {
            this.failedStep = step;
            this.failureReason = reason;
            moveTo(ChainStatus.COMPENSATING, step + " failed: " + reason);
        }
    }

    void undone(ChainStep step, String what) {
        if (!compensatedSteps.contains(step)) {
            compensatedSteps.add(step);
        }
        log("undo " + step + ": " + what);
    }

    void compensated() {
        moveTo(ChainStatus.COMPENSATED, "compensation finished, undone " + compensatedSteps);
    }

    void cancelled(long refundCents) {
        this.refundCents = refundCents;
        moveTo(ChainStatus.CANCELLED, "cancelled by admin, refunded " + refundCents + "c");
    }

    void note(String message) {
        log(message);
    }

    private void moveTo(ChainStatus next, String message) {
        this.status = next;
        log(message + " -> " + next);
    }

    private void log(String message) {
        history.add(Instant.now() + "  " + message);
    }

    public String orderId() { return orderId; }
    public String buyerId() { return buyerId; }
    public String email() { return email; }
    public String seatCode() { return seatCode; }
    public String zone() { return zone; }
    public ChainStatus status() { return status; }
    public String holdId() { return holdId; }
    public String ticketId() { return ticketId; }
    public String passId() { return passId; }
    public String gateId() { return gateId; }
    public long priceCents() { return priceCents; }
    public ChainStep failedStep() { return failedStep; }
    public String failureReason() { return failureReason; }
    public List<ChainStep> compensatedSteps() { return List.copyOf(compensatedSteps); }
    public Long refundCents() { return refundCents; }
    public List<String> history() { return List.copyOf(history); }
}
