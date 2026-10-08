package concert.ticketing;

public class FraudRejectedException extends RuntimeException {
    public FraudRejectedException(String reason) {
        super("Fraud check rejected the ticket: " + reason);
    }
}
