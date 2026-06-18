package com.autodispatch.dispatch.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full ride view for admin use only — rider name/phone and driver whatsappId
 * are unmasked. {@code driverId} and related driver fields are null when no
 * driver has been assigned yet.
 */
public record AdminRideView(
        UUID id,
        UUID riderId, String riderName, String riderPhone,
        UUID driverId, String driverName, String driverWhatsappId, String driverVehicleNo,
        String pickupLabel, String dropLabel, String status,
        BigDecimal fareAmount,
        Instant requestedAt, Instant assignedAt, Instant completedAt,
        String cancelReason) {
}
