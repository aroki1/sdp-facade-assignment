package concert.facade;

import java.util.List;

public class PurchaseFailedException extends RuntimeException {
    private final String orderId;
    private final ChainStep failedStep;
    private final List<ChainStep> compensatedSteps;

    PurchaseFailedException(String orderId, ChainStep failedStep, List<ChainStep> compensatedSteps,
                            String reason, Throwable cause) {
        super("Order " + orderId + " failed at " + failedStep + " (" + reason + ")"
                + (compensatedSteps.isEmpty() ? "; nothing had to be undone"
                : "; undone in order: " + compensatedSteps), cause);
        this.orderId = orderId;
        this.failedStep = failedStep;
        this.compensatedSteps = List.copyOf(compensatedSteps);
    }

    public String orderId() { return orderId; }
    public ChainStep failedStep() { return failedStep; }
    public List<ChainStep> compensatedSteps() { return compensatedSteps; }
}
