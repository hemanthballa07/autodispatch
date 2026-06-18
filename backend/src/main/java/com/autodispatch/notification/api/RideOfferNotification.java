package com.autodispatch.notification.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Channel-agnostic payload for a ride offer sent to a driver. The gateway
 * decides presentation (interactive button vs. plain text).
 */
public record RideOfferNotification(
        UUID rideId,
        String pickupLabel,
        String dropLabel,
        BigDecimal fareAmount,
        String rideCode,
        long replyWindowSeconds) {
}
