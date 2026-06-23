package com.autodispatch.dispatch.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rider-facing booking operations on rides. The fare amount is the quote
 * captured at request time — immutable for the ride's lifetime.
 */
public interface RideBooking {

    /**
     * Creates a REQUESTED ride immediately dispatched, or a SCHEDULED ride
     * released by the sweeper at {@code scheduledFor}.
     *
     * @throws ActiveRideExistsException if the rider already has an active ride
     */
    UUID requestRide(UUID riderId, String pickupLabel, String dropLabel, BigDecimal quotedFare,
                     UUID vehicleTypeId, UUID pickupLocationId, UUID dropLocationId, Instant scheduledFor);

    /**
     * Backward-compatible overload: creates an immediate REQUESTED ride.
     *
     * @throws ActiveRideExistsException if the rider already has an active ride
     */
    default UUID requestRide(UUID riderId, String pickupLabel, String dropLabel, BigDecimal quotedFare) {
        return requestRide(riderId, pickupLabel, dropLabel, quotedFare, null, null, null, null);
    }

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
