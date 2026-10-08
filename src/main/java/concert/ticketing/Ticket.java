package concert.ticketing;

/** Entity: ISSUED -> ACTIVATED -> VOIDED. */
public class Ticket {
    public enum Status {
        ISSUED, ACTIVATED, VOIDED;

        boolean canMoveTo(Status next) {
            return switch (this) {
                case ISSUED -> next == ACTIVATED || next == VOIDED;
                case ACTIVATED -> next == VOIDED;
                case VOIDED -> false;
            };
        }
    }

    private String id;
    private String reference;
    private String holderId;
    private String email;
    private String description;
    private long priceCents;
    private String barcode;
    private Status status;
    private String voidReason;

    private Ticket() {
    }

    Ticket(String id, String reference, String holderId, String email, String description, long priceCents) {
        this.id = id;
        this.reference = reference;
        this.holderId = holderId;
        this.email = email;
        this.description = description;
        this.priceCents = priceCents;
        this.status = Status.ISSUED;
    }

    void stampBarcode(String barcode) {
        this.barcode = barcode;
    }

    void activate() {
        moveTo(Status.ACTIVATED);
    }

    void voidTicket(String reason) {
        moveTo(Status.VOIDED);
        this.voidReason = reason;
    }

    private void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Ticket " + id + " cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public boolean isLive() {
        return status != Status.VOIDED;
    }

    public String id() { return id; }
    public String reference() { return reference; }
    public String holderId() { return holderId; }
    public String email() { return email; }
    public String description() { return description; }
    public long priceCents() { return priceCents; }
    public String barcode() { return barcode; }
    public Status status() { return status; }
    public String voidReason() { return voidReason; }

    @Override
    public String toString() {
        return "Ticket[" + id + " holder=" + holderId + " '" + description + "' barcode=" + barcode
                + " status=" + status + (voidReason == null ? "" : " reason='" + voidReason + "'") + "]";
    }
}
