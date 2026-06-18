package com.autodispatch.dispatch.api;

import java.util.UUID;

public class RideNotCancellableException extends RuntimeException {

    private final String currentStatus;

    public RideNotCancellableException(UUID rideId, String currentStatus) {
        super("Ride %s cannot be cancelled in state %s".formatted(rideId, currentStatus));
        this.currentStatus = currentStatus;
    }

    public String currentStatus() {
        return currentStatus;
    }
}
