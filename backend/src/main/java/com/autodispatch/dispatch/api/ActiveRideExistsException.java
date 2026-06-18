package com.autodispatch.dispatch.api;

import java.util.UUID;

public class ActiveRideExistsException extends RuntimeException {

    public ActiveRideExistsException(UUID riderId) {
        super("Rider %s already has an active ride".formatted(riderId));
    }
}
