package com.autodispatch.rating.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface RideRatingRepository extends JpaRepository<RideRating, UUID> {
    Optional<RideRating> findByRideIdAndRaterRiderId(UUID rideId, UUID raterRiderId);
    long countByDriverId(UUID driverId);
    @Query("SELECT AVG(r.driverStars) FROM RideRating r WHERE r.driverId = :driverId")
    Double findAverageStarsByDriverId(@Param("driverId") UUID driverId);
}
