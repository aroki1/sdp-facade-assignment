package concert.access;

/** Entity: ACTIVE -> USED | REVOKED. */
public class AccessPass {
    public enum Status {
        ACTIVE, USED, REVOKED;

        boolean canMoveTo(Status next) {
            return this == ACTIVE && (next == USED || next == REVOKED);
        }
    }

    private String id;
    private String reference;
    private String holderId;
    private String zone;
    private String gateId;
    private long paidCents;
    private Status status;
    private String revokeReason;

    private AccessPass() {
    }

    AccessPass(String id, String reference, String holderId, String zone, String gateId, long paidCents) {
        this.id = id;
        this.reference = reference;
        this.holderId = holderId;
        this.zone = zone;
        this.gateId = gateId;
        this.paidCents = paidCents;
        this.status = Status.ACTIVE;
    }

    void markUsed() {
        moveTo(Status.USED);
    }

    void revoke(String reason) {
        moveTo(Status.REVOKED);
        this.revokeReason = reason;
    }

    private void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Access pass " + id + " cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public boolean countsAgainstCapacity() {
        return status != Status.REVOKED;
    }

    public String id() { return id; }
    public String reference() { return reference; }
    public String holderId() { return holderId; }
    public String zone() { return zone; }
    public String gateId() { return gateId; }
    public long paidCents() { return paidCents; }
    public Status status() { return status; }
    public String revokeReason() { return revokeReason; }

    @Override
    public String toString() {
        return "AccessPass[" + id + " holder=" + holderId + " zone=" + zone + " gate=" + gateId
                + " status=" + status + (revokeReason == null ? "" : " reason='" + revokeReason + "'") + "]";
    }
}
