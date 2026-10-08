package concert.ticketing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Business logic: signed barcode and printable ticket. */
public class TicketPrinter {
    private final String signingSecret;

    public TicketPrinter(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public String barcodeFor(String ticketId, String holderId) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest((ticketId + "|" + holderId + "|" + signingSecret)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean isGenuine(Ticket ticket) {
        return barcodeFor(ticket.id(), ticket.holderId()).equals(ticket.barcode());
    }

    public String render(Ticket ticket) {
        String line = "+------------------------------------------+";
        return String.join(System.lineSeparator(),
                line,
                row("CONCERT TICKET   " + ticket.id()),
                row(ticket.description()),
                row("Holder : " + ticket.holderId()),
                row(String.format("Price  : $%d.%02d", ticket.priceCents() / 100, ticket.priceCents() % 100)),
                row("Barcode: " + ticket.barcode()),
                row("Status : " + ticket.status()),
                line);
    }

    private static String row(String text) {
        String t = text.length() > 40 ? text.substring(0, 40) : text;
        return "| " + t + " ".repeat(40 - t.length()) + " |";
    }
}
