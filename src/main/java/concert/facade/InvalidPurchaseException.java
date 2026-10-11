package concert.facade;

public class InvalidPurchaseException extends RuntimeException {
    public InvalidPurchaseException(String message) {
        super(message);
    }
}
