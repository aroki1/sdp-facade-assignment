package concert.facade;

public enum ChainStatus {
    PENDING_A,
    PENDING_B,
    PENDING_C,
    COMPLETED,
    REJECTED,
    COMPENSATING,
    COMPENSATED,
    CANCELLED;

    public boolean isUnfinished() {
        return this == PENDING_A || this == PENDING_B || this == PENDING_C || this == COMPENSATING;
    }
}
