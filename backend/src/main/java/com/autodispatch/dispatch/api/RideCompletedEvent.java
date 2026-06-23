package com.autodispatch.dispatch.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Published after a ride transitions to COMPLETED and its transaction commits. */
public record RideCompletedEvent(UUID rideId, UUID riderId, UUID driverId, BigDecimal fareAmount) {}
