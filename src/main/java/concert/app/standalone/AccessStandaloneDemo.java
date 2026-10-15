package concert.app.standalone;

import concert.access.AccessControlGate;
import concert.access.AccessDeniedException;
import concert.access.AccessPass;
import concert.access.AccessRepository;
import concert.access.AccessService;
import concert.access.RefundService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public final class AccessStandaloneDemo {
    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : "data-standalone");
        AccessService access = new AccessService(new AccessRepository(dir), new AccessControlGate(),
                new RefundService(), LocalDate.now().plusDays(10), Clock.systemDefaultZone());
        access.configureGate("EAST", List.of("STANDING"), 500);
        access.openGate("EAST");

        AccessPass pass = access.grant("STANDALONE", "standalone-fan", "STANDING", 3_000);
        System.out.println("Granted:  " + pass);
        System.out.println("Refund (10 days out): " + access.refund(pass.id()));

        AccessPass second = access.grant("STANDALONE-2", "standalone-fan", "STANDING", 3_000);
        System.out.println("Scanned:  " + access.scan(second.id()));

        access.closeGate("EAST");
        try {
            access.grant("STANDALONE-3", "late-fan", "STANDING", 3_000);
        } catch (AccessDeniedException e) {
            System.out.println(e.getMessage());
        }
        access.openGate("EAST");
    }
}
