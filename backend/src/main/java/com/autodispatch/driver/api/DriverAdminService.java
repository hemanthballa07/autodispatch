package com.autodispatch.driver.api;

import java.util.List;
import java.util.UUID;

/**
 * Admin operations on drivers (registration, verification, suspension).
 * {@code findById} for existence checks lives on {@link DriverAvailabilityService}.
 */
public interface DriverAdminService {

    /** @throws DriverAlreadyExistsException if whatsappId is already registered */
    DriverSummary register(String name, String whatsappId, String vehicleNo);

    DriverSummary verify(UUID driverId);

    /** @throws DriverOnRideException if driver is ON_RIDE */
    DriverSummary suspend(UUID driverId);

    /** Clears suspended flag; driver stays OFFLINE until they send WhatsApp ON. */
    DriverSummary unsuspend(UUID driverId);

    List<DriverSummary> listAll();
}
