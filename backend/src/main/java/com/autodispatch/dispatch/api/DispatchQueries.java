package com.autodispatch.dispatch.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-side dispatch lookups for adapters (e.g. resolving WhatsApp text
 * commands against a driver's rides). Public API — adapters never touch
 * dispatch internals.
 */
public interface DispatchQueries {

    /** The driver's single active ride (ASSIGNED/ARRIVED/IN_PROGRESS), if any. */
    Optional<UUID> findActiveRideForDriver(UUID driverId);

    /**
     * Resolves a short ride code (see RideCodes) against the driver's open
     * offers on BROADCASTING rides.
     */
    Optional<UUID> resolveOpenOfferByCode(UUID driverId, String rideCode);
}
