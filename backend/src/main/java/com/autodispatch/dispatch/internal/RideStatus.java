package com.autodispatch.dispatch.internal;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ride lifecycle with the explicit allowed-transitions map (locked design):
 *
 * <pre>
 * SCHEDULED    → REQUESTED | CANCELLED  (sweeper releases at scheduled_for time)
 * REQUESTED    → BROADCASTING | CANCELLED
 * BROADCASTING → ASSIGNED | EXPIRED | CANCELLED
 * ASSIGNED     → ARRIVED | CANCELLED | BROADCASTING (driver-cancel, max 1 — guarded in Ride)
 * ARRIVED      → IN_PROGRESS
 * IN_PROGRESS  → COMPLETED
 * COMPLETED / CANCELLED / EXPIRED are terminal.
 * </pre>
 */
public enum RideStatus {
    SCHEDULED,
    REQUESTED,
    BROADCASTING,
    ASSIGNED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(SCHEDULED, EnumSet.of(REQUESTED, CANCELLED)),
            Map.entry(REQUESTED, EnumSet.of(BROADCASTING, CANCELLED)),
            Map.entry(BROADCASTING, EnumSet.of(ASSIGNED, EXPIRED, CANCELLED)),
            Map.entry(ASSIGNED, EnumSet.of(ARRIVED, CANCELLED, BROADCASTING)),
            Map.entry(ARRIVED, EnumSet.of(IN_PROGRESS)),
            Map.entry(IN_PROGRESS, EnumSet.of(COMPLETED)),
            Map.entry(COMPLETED, EnumSet.noneOf(RideStatus.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(RideStatus.class)),
            Map.entry(EXPIRED, EnumSet.noneOf(RideStatus.class)));

    public boolean canTransitionTo(RideStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
