package com.autodispatch.driver.api;

import java.util.UUID;

/**
 * Domain exception: an availability change was rejected because the driver is
 * currently ON_RIDE.
 */
public class DriverOnRideException extends RuntimeException {

    public DriverOnRideException(UUID driverId, String operation) {
        super("Driver %s is ON_RIDE; %s rejected".formatted(driverId, operation));
    }
}
