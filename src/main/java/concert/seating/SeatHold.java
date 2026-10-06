package concert.seating;

import java.util.Set;

/** Entity: HELD -> CONFIRMED -> RELEASED. */
public class SeatHold {
    public enum Status {
        HELD, CONFIRMED, RELEASED;

        boolean canMoveTo(Status next) {
            return switch (this) {
                case HELD -> Set.of(CONFIRMED, RELEASED).contains(next);
                case CONFIRMED -> next == RELEASED;
                case RELEASED -> false;
            };
        }
    }

    private String id;
    private String reference;
    private String seatCode;
    private SeatingChart.Section section;
    private String holderId;
    private long priceCents;
    private Status status;
    private String releaseReason;

    private SeatHold() {
    }

    SeatHold(String id, String reference, String seatCode, SeatingChart.Section section,
             String holderId, long priceCents) {
        this.id = id;
        this.reference = reference;
        this.seatCode = seatCode;
        this.section = section;
        this.holderId = holderId;
        this.priceCents = priceCents;
        this.status = Status.HELD;
    }

    void confirm() {
        moveTo(Status.CONFIRMED);
    }

    void release(String reason) {
        moveTo(Status.RELEASED);
        this.releaseReason = reason;
    }

    private void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Seat hold " + id + " cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public boolean isActive() {
        return status != Status.RELEASED;
    }

    public String id() { return id; }
    public String reference() { return reference; }
    public String seatCode() { return seatCode; }
    public SeatingChart.Section section() { return section; }
    public String holderId() { return holderId; }
    public long priceCents() { return priceCents; }
    public Status status() { return status; }
    public String releaseReason() { return releaseReason; }

    @Override
    public String toString() {
        return "SeatHold[" + id + " seat=" + seatCode + " " + section + " holder=" + holderId
                + " price=" + priceCents + "c status=" + status
                + (releaseReason == null ? "" : " reason='" + releaseReason + "'") + "]";
    }
}
