package com.autodispatch.dispatch.internal;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ride lifecycle with the explicit allowed-transitions map (locked design):
 *
 * <pre>
 * REQUESTED    → BROADCASTING | CANCELLED
 * BROADCASTING → ASSIGNED | EXPIRED | CANCELLED
 * ASSIGNED     → ARRIVED | CANCELLED | BROADCASTING (driver-cancel, max 1 — guarded in Ride)
 * ARRIVED      → IN_PROGRESS
 * IN_PROGRESS  → COMPLETED
 * COMPLETED / CANCELLED / EXPIRED are terminal.
 * </pre>
 */
public enum RideStatus {
    REQUESTED,
    BROADCASTING,
    ASSIGNED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED_TRANSITIONS = Map.of(
            REQUESTED, EnumSet.of(BROADCASTING, CANCELLED),
            BROADCASTING, EnumSet.of(ASSIGNED, EXPIRED, CANCELLED),
            ASSIGNED, EnumSet.of(ARRIVED, CANCELLED, BROADCASTING),
            ARRIVED, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(RideStatus.class),
            CANCELLED, EnumSet.noneOf(RideStatus.class),
            EXPIRED, EnumSet.noneOf(RideStatus.class));

    public boolean canTransitionTo(RideStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
