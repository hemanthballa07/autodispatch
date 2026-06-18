package com.autodispatch.dispatch.api;

/**
 * A dispatch command was rejected because the driver is unknown or not verified.
 */
public class DriverNotEligibleException extends RuntimeException {

    public DriverNotEligibleException(String message) {
        super(message);
    }
}
