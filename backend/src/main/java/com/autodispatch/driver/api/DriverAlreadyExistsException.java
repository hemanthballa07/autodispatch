package com.autodispatch.driver.api;

public class DriverAlreadyExistsException extends RuntimeException {

    public DriverAlreadyExistsException(String whatsappId) {
        super("Driver with whatsappId '%s' already exists".formatted(whatsappId));
    }
}
