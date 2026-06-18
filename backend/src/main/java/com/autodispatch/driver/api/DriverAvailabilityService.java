package com.autodispatch.driver.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driver availability port. Backing invariant: the Redis SET
 * {@code drivers:available} mirrors DB driver status. Every write updates the
 * DB first, then Redis; on any disagreement the DB wins and Redis is repaired.
 */
public interface DriverAvailabilityService {

    /** OFFLINE → AVAILABLE (DB first, then Redis SADD). Rejected while ON_RIDE. */
    void goOnline(UUID driverId);

    /**
     * AVAILABLE/OFFLINE → OFFLINE (DB first, then Redis SREM).
     *
     * @throws DriverOnRideException if the driver is ON_RIDE
     */
    void goOffline(UUID driverId);

    /** Available AND verified drivers; repairs Redis against the DB on the way. */
    List<DriverSummary> listAvailableVerified();

    Optional<DriverSummary> findByWhatsappId(String whatsappId);

    Optional<DriverSummary> findById(UUID driverId);

    /**
     * Atomically AVAILABLE → ON_RIDE (conditional update from Phase 2), then
     * removes the driver from the Redis available set on success.
     *
     * @return true if this caller won the conditional update
     */
    boolean tryMarkOnRide(UUID driverId);

    /** Returns a driver to AVAILABLE (DB first, then Redis SADD). */
    void makeAvailable(UUID driverId);

    /** Records an inbound message from the driver (WhatsApp 24h session rule). */
    void recordInbound(UUID driverId);
}
