# Concert Box Office — Facade over a 3-step chain

Buying a seat runs three independent subsystems in order:

| Step | Subsystem | Does | Can fail when |
|---|---|---|---|
| A | `seating` — Seating Chart, Dynamic Pricing | holds the seat | seat taken / doesn't exist |
| B | `ticketing` — Fraud Checker, Ticket Printer | prints the ticket | buyer blocklisted, bad e-mail, >4 tickets |
| C | `access` — Access Control Gate, Refund Service | grants a gate pass | gate closed or full |

The subsystems don't know about each other. They only meet in `concert.facade`.

## Run

```bash
mvn clean verify
```

```bash
java -jar target/concert-boxoffice.jar
```

`mvn clean verify` runs the tests plus a JaCoCo check that fails the build below 80% line coverage. The report is at `target/site/jacoco/index.html`.

State is saved as JSON in `./data/`. Delete that folder to start fresh.

Each subsystem also runs alone: `concert.app.standalone.*StandaloneDemo`.

## Design

- **Two facades.**
  - `TicketPurchaseFacade` is the client side: `purchase`, `resume`, `resumeInterrupted`.
  - `BoxOfficeAdminFacade` is for admins: inspect, cancel + refund, manual compensation, blocklist, gates.
- **Chain progress is persisted.** Each order's status is saved after every step: `PENDING_A → PENDING_B → PENDING_C → COMPLETED`, or `REJECTED` / `COMPENSATING → COMPENSATED`.
- **Compensation.**
  - If B fails, A is undone.
  - If C fails, B is undone, then A.
  - `ChainCompensator` reads what each subsystem actually has on disk for the order, so it also works after a crash.
- **Restart.** After a restart, `resumeInterrupted()` continues each unfinished order from the status saved on disk.

## Least Knowledge — before / after

Without the facade, the client knows every subsystem and does the rollback itself:

```java
SeatHold hold = seating.hold("C7", "alice", ref);
try {
    Ticket t = ticketing.issue(ref, "alice", mail, hold.section().name(), hold.priceCents());
    try {
        access.grant(ref, "alice", hold.section().name(), hold.priceCents());
    } catch (AccessDeniedException e) {
        ticketing.voidTicket(t.id(), "gate");
        seating.release(hold.id(), "gate");
    }
} catch (FraudRejectedException e) {
    seating.release(hold.id(), "fraud");
}
```

With the facade, the client has one friend:

```java
purchases.purchase(new PurchaseRequest("alice", mail, "C7"));
```

## Facade vs Adapter

An Adapter changes the shape of one interface so it fits the interface a client expects. Our subsystems already have usable interfaces; nothing needs converting.

The client needs a new operation, "buy a seat", that no single subsystem offers. It exists only as coordination: the order of the steps, saving progress, and undoing B then A when C fails.

That is the Facade's intent: give one simple interface over several subsystems and own the logic between them. An adapter would leave the client still calling three services and writing the rollback itself.

## Tests

- `ConcertVenue` is a test helper that makes tests read like sentences, e.g. `venue.fan("mallory").buysSeat("C5").failsAt(TICKETING).afterUndoing(SEATING)`.
- Each test uses its own temp folder and a fixed clock.
- Key tests:
  - B fails after A: `PurchaseChainTest.fraudRejectionAfterTheSeatWasHeldUndoesTheSeatHoldOnly`
  - C fails after A and B: `PurchaseChainTest.closedGateAfterSeatAndTicketCommittedUndoesTicketThenSeat`
  - Restart and recovery: `RestartRecoveryTest`
- Line coverage is about 98% across all three subsystems and the facades.

## Demo

| Key | Shows |
|---|---|
| `2` | buy a ticket (happy path) |
| `S1` | B fails → A undone |
| `S2` | C fails → B, then A undone |
| `4` | inspect an order in all subsystems |
| `10` | crash the app after a chosen step; restart it, then press `11` to resume |
| `7` | cancel a completed order with a refund |

## UML

[docs/class-diagram.svg](docs/class-diagram.svg)
