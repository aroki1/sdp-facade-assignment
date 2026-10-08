package concert.ticketing;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Business logic: blocklist, e-mail and ticket-limit rules. */
public class FraudChecker {
    public static final int MAX_LIVE_TICKETS_PER_BUYER = 4;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[a-z]{2,}$");
    private static final Set<String> DISPOSABLE_DOMAINS =
            Set.of("mailinator.com", "tempmail.com", "10minutemail.com", "trashmail.com");

    public record Verdict(boolean approved, String reason) {
        static Verdict approve() {
            return new Verdict(true, null);
        }

        static Verdict reject(String reason) {
            return new Verdict(false, reason);
        }
    }

    public Verdict assess(String buyerId, String email, boolean buyerBlocklisted, int liveTicketsOfBuyer) {
        if (buyerBlocklisted) {
            return Verdict.reject("buyer '" + buyerId + "' is on the fraud blocklist");
        }
        String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(mail).matches()) {
            return Verdict.reject("e-mail '" + email + "' is not a valid address");
        }
        String domain = mail.substring(mail.indexOf('@') + 1);
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            return Verdict.reject("disposable e-mail domain '" + domain + "' is not accepted");
        }
        if (liveTicketsOfBuyer >= MAX_LIVE_TICKETS_PER_BUYER) {
            return Verdict.reject("buyer '" + buyerId + "' already holds " + liveTicketsOfBuyer
                    + " tickets (limit " + MAX_LIVE_TICKETS_PER_BUYER + ")");
        }
        return Verdict.approve();
    }
}
