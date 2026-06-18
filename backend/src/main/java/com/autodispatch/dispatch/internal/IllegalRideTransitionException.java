package com.autodispatch.dispatch.internal;

import java.util.UUID;

/**
 * Thrown by {@link Ride#transitionTo} for any edge not in the allowed-transitions
 * map, or when the rebroadcast guard is exhausted.
 */
public class IllegalRideTransitionException extends RuntimeException {

    public IllegalRideTransitionException(UUID rideId, RideStatus from, RideStatus to) {
        this(rideId, from, to, "transition not allowed");
    }

    public IllegalRideTransitionException(UUID rideId, RideStatus from, RideStatus to, String reason) {
        super("Illegal ride transition %s -> %s for ride %s: %s".formatted(from, to, rideId, reason));
    }
}
