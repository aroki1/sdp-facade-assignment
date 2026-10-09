package concert.access;

public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String reason) {
        super("Access denied: " + reason);
    }
}
