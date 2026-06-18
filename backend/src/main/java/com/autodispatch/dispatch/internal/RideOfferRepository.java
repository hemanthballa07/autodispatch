package com.autodispatch.dispatch.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideOfferRepository extends JpaRepository<RideOffer, UUID> {

    List<RideOffer> findByRideId(UUID rideId);

    Optional<RideOffer> findByRideIdAndDriverId(UUID rideId, UUID driverId);
}
