package concert.access;

import java.util.List;

/** Business logic: which gate serves a zone and whether it can admit. */
public class AccessControlGate {
    public record Gate(String id, List<String> zones, int capacity, boolean open) {
        public boolean serves(String zone) {
            return zones.contains(zone);
        }

        Gate withOpen(boolean nowOpen) {
            return new Gate(id, zones, capacity, nowOpen);
        }

        Gate withCapacity(int newCapacity) {
            return new Gate(id, zones, newCapacity, open);
        }
    }

    public Gate gateFor(String zone, List<Gate> gates) {
        return gates.stream()
                .filter(g -> g.serves(zone))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("no entrance gate serves zone '" + zone + "'"));
    }

    public void checkAdmission(Gate gate, int passesAtGate) {
        if (!gate.open()) {
            throw new AccessDeniedException("gate " + gate.id() + " is closed");
        }
        if (passesAtGate >= gate.capacity()) {
            throw new AccessDeniedException("gate " + gate.id() + " is at capacity (" + gate.capacity() + ")");
        }
    }

    public void checkScan(AccessPass pass, Gate gate) {
        if (pass.status() != AccessPass.Status.ACTIVE) {
            throw new AccessDeniedException("pass " + pass.id() + " is " + pass.status());
        }
        if (!gate.open()) {
            throw new AccessDeniedException("gate " + gate.id() + " is closed");
        }
    }
}
