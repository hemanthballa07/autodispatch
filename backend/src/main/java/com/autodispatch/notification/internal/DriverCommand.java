package com.autodispatch.notification.internal;

import java.util.UUID;

/**
 * Anti-corruption layer: every inbound WhatsApp message is translated into
 * exactly one of these commands before touching any domain API.
 */
sealed interface DriverCommand {

    record GoOnline() implements DriverCommand {
    }

    record GoOffline() implements DriverCommand {
    }

    record AcceptRide(UUID rideId) implements DriverCommand {
    }

    record MarkArrived() implements DriverCommand {
    }

    record StartTrip() implements DriverCommand {
    }

    record CompleteTrip() implements DriverCommand {
    }

    record CancelRide() implements DriverCommand {
    }

    record Unrecognized(String rawText) implements DriverCommand {
    }
}
