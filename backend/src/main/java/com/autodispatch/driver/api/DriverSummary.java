package com.autodispatch.driver.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a driver exposed to other modules.
 * {@code lastInboundAt} backs the WhatsApp 24h session rule (nullable: never
 * messaged us).
 */
public record DriverSummary(
        UUID id,
        String name,
        String whatsappId,
        String vehicleNo,
        boolean verified,
        boolean suspended,
        DriverState state,
        Instant lastInboundAt,
        Instant createdAt) {
}
