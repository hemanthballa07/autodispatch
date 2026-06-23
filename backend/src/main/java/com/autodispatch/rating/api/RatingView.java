package com.autodispatch.rating.api;

import java.time.Instant;
import java.util.UUID;

public record RatingView(UUID id, UUID rideId, UUID driverId, UUID raterRiderId,
                          int driverStars, String comment, Instant createdAt) {}
