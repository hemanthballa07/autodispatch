package com.autodispatch.rating.api;

import java.util.Optional;
import java.util.UUID;

public interface RatingService {
    RatingView rateDriver(UUID rideId, UUID riderIdFromAuth, int stars, String comment);
    Optional<RatingView> findByRideAndRider(UUID rideId, UUID riderId);
    DriverRatingStats getDriverStats(UUID driverId);
}
