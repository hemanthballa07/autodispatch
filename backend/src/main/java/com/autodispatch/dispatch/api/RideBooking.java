package com.autodispatch.dispatch.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rider-facing booking operations on rides. The fare amount is the quote
 * captured at request time — immutable for the ride's lifetime.
 */
public interface RideBooking {

    /**
     * Creates a REQUESTED ride with the quoted fare.
     *
     * @throws ActiveRideExistsException if the rider already has an active ride
     */
    UUID requestRide(UUID riderId, String pickupLabel, String dropLabel, BigDecimal quotedFare);

    Optional<RideView> findRide(UUID rideId);

    /** Rider's rides, newest first. */
    List<RideView> ridesForRider(UUID riderId, int page, int size);

    /**
     * Rider-initiated cancel; delegates to the domain transition.
     *
     * @throws RideNotCancellableException if the current state disallows it
     */
    void cancelByRider(UUID rideId);
}
