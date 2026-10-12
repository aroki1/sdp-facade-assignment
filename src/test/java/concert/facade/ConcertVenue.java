package concert.facade;

import concert.access.AccessPass;
import concert.access.AccessRepository;
import concert.seating.SeatHold;
import concert.seating.SeatHoldRepository;
import concert.ticketing.Ticket;
import concert.ticketing.TicketRepository;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class ConcertVenue {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    static final LocalDate SHOW_DAY = TODAY.plusDays(20);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T18:00:00Z"), ZoneOffset.UTC);

    static final class SimulatedCrash extends RuntimeException {
        SimulatedCrash(ChainStatus at) {
            super("power cut right after the order reached " + at);
        }
    }

    private final Path dataDir;
    private BoxOffice office;
    private ChainStatus crashWhenReaching;

    private ConcertVenue(Path dataDir) {
        this.dataDir = dataDir;
        this.office = open();
    }

    static ConcertVenue freshVenueIn(Path emptyDir) {
        return new ConcertVenue(emptyDir);
    }

    private BoxOffice open() {
        return BoxOffice.open(dataDir, SHOW_DAY, CLOCK, order -> {
            if (order.status() == crashWhenReaching) {
                crashWhenReaching = null;
                throw new SimulatedCrash(order.status());
            }
        });
    }

    ConcertVenue buyerIsBlocklisted(String buyer) {
        office.admin().blockBuyer(buyer);
        return this;
    }

    ConcertVenue gateIsClosed(String gate) {
        office.admin().closeGate(gate);
        return this;
    }

    ConcertVenue gateReopens(String gate) {
        office.admin().openGate(gate);
        return this;
    }

    ConcertVenue systemCrashesWhenAnOrderReaches(ChainStatus status) {
        this.crashWhenReaching = status;
        return this;
    }

    ConcertVenue restart() {
        this.crashWhenReaching = null;
        this.office = open();
        return this;
    }

    TicketPurchaseFacade purchases() {
        return office.purchases();
    }

    BoxOfficeAdminFacade admin() {
        return office.admin();
    }

    Fan fan(String buyerId) {
        return new Fan(buyerId);
    }

    final class Fan {
        private final String buyerId;

        private Fan(String buyerId) {
            this.buyerId = buyerId;
        }

        Attempt buysSeat(String seat) {
            return buysSeatUsing(buyerId + "@example.com", seat);
        }

        Attempt buysSeatUsing(String email, String seat) {
            try {
                return new Attempt(office.purchases().purchase(new PurchaseRequest(buyerId, email, seat)), null);
            } catch (PurchaseFailedException e) {
                return new Attempt(null, e);
            }
        }
    }

    final class Attempt {
        private final PurchaseReceipt receipt;
        private final PurchaseFailedException failure;

        private Attempt(PurchaseReceipt receipt, PurchaseFailedException failure) {
            this.receipt = receipt;
            this.failure = failure;
        }

        PurchaseReceipt succeeds() {
            assertNull(failure, () -> "expected success but got: " + failure.getMessage());
            order(receipt.orderId()).isPersistedAs(ChainStatus.COMPLETED);
            return receipt;
        }

        Attempt failsAt(ChainStep step) {
            assertNotNull(failure, "expected the purchase to fail");
            assertEquals(step, failure.failedStep(), failure.getMessage());
            return this;
        }

        Attempt afterUndoing(ChainStep... stepsInOrder) {
            List<ChainStep> expected = Arrays.asList(stepsInOrder);
            assertEquals(expected, failure.compensatedSteps());
            OrderRecord persisted = order(failure.orderId()).record();
            assertEquals(expected, persisted.compensatedSteps());
            assertEquals(ChainStatus.COMPENSATED, persisted.status());
            return this;
        }

        Attempt withNothingToUndo() {
            assertTrue(failure.compensatedSteps().isEmpty());
            order(failure.orderId()).isPersistedAs(ChainStatus.REJECTED);
            return this;
        }

        String orderId() {
            return receipt != null ? receipt.orderId() : failure.orderId();
        }
    }

    SeatCheck seat(String seat) {
        return new SeatCheck(seat);
    }

    final class SeatCheck {
        private final String seat;

        private SeatCheck(String seat) {
            this.seat = seat;
        }

        void isFreeAgain() {
            assertTrue(new SeatHoldRepository(dataDir).findActiveBySeat(seat).isEmpty(), seat + " should be free");
        }

        void isSoldTo(String buyer) {
            SeatHold hold = new SeatHoldRepository(dataDir).findActiveBySeat(seat).orElseThrow();
            assertEquals(SeatHold.Status.CONFIRMED, hold.status());
            assertEquals(buyer, hold.holderId());
        }

        void isHeldBy(String buyer) {
            SeatHold hold = new SeatHoldRepository(dataDir).findActiveBySeat(seat).orElseThrow();
            assertEquals(SeatHold.Status.HELD, hold.status());
            assertEquals(buyer, hold.holderId());
        }
    }

    OrderCheck order(String orderId) {
        return new OrderCheck(orderId);
    }

    final class OrderCheck {
        private final String orderId;

        private OrderCheck(String orderId) {
            this.orderId = orderId;
        }

        OrderRecord record() {
            return new OrderJournal(dataDir).find(orderId).orElseThrow(() -> new AssertionError("no order " + orderId));
        }

        OrderCheck isPersistedAs(ChainStatus status) {
            assertEquals(status, record().status());
            return this;
        }

        OrderCheck hasTicket(Ticket.Status status) {
            Optional<Ticket> ticket = new TicketRepository(dataDir).findByReference(orderId);
            assertEquals(status, ticket.orElseThrow(() -> new AssertionError("no ticket on disk")).status());
            return this;
        }

        OrderCheck hasNoTicket() {
            assertTrue(new TicketRepository(dataDir).findByReference(orderId).isEmpty());
            return this;
        }

        OrderCheck hasPass(AccessPass.Status status) {
            Optional<AccessPass> pass = new AccessRepository(dataDir).findPassByReference(orderId);
            assertEquals(status, pass.orElseThrow(() -> new AssertionError("no pass on disk")).status());
            return this;
        }

        OrderCheck hasNoPass() {
            assertTrue(new AccessRepository(dataDir).findPassByReference(orderId).isEmpty());
            return this;
        }

        OrderCheck undidInThisOrder(ChainStep... steps) {
            List<String> undos = record().history().stream().filter(h -> h.contains("undo ")).toList();
            assertEquals(steps.length, undos.size(), undos::toString);
            for (int i = 0; i < steps.length; i++) {
                assertTrue(undos.get(i).contains("undo " + steps[i]), undos::toString);
            }
            return this;
        }
    }
}
