package com.autodispatch.dispatch.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only ride projection for adapters/UIs. Status is the RideStatus name.
 */
public record RideView(
        UUID id,
        UUID riderId,
        UUID driverId,
        String pickupLabel,
        String dropLabel,
        String status,
        BigDecimal fareAmount,
        Instant requestedAt,
        Instant assignedAt,
        Instant completedAt,
        String cancelReason) {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "CANCELLED", "EXPIRED");

    public boolean terminal() {
        return TERMINAL.contains(status);
    }
}
