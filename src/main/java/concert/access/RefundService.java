package concert.access;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Business logic: refund policy (100% / 50% / 0%). */
public class RefundService {
    public record Refund(String passId, String reference, long amountCents, int percent, String issuedOn) {
    }

    public int refundPercent(LocalDate today, LocalDate eventDate) {
        long days = ChronoUnit.DAYS.between(today, eventDate);
        if (days >= 14) {
            return 100;
        }
        if (days >= 3) {
            return 50;
        }
        return 0;
    }

    public Refund computeRefund(AccessPass pass, LocalDate today, LocalDate eventDate) {
        if (pass.status() == AccessPass.Status.USED) {
            throw new IllegalStateException("Pass " + pass.id() + " was already used at the gate; no refund");
        }
        int percent = refundPercent(today, eventDate);
        long amount = pass.paidCents() * percent / 100;
        return new Refund(pass.id(), pass.reference(), amount, percent, today.toString());
    }
}
