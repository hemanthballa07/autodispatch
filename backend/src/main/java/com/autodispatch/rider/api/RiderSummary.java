package com.autodispatch.rider.api;

import java.util.UUID;

/**
 * Read-only view of a rider exposed to other modules.
 */
public record RiderSummary(UUID id, String name, String phone) {
}
