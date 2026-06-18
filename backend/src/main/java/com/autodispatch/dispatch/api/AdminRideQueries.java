package com.autodispatch.dispatch.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only admin queries over rides. Implementations live in dispatch.internal.
 */
public interface AdminRideQueries {

    /**
     * @param status null = no filter; invalid status name throws {@link IllegalArgumentException}
     * @param date   null = no filter; matches rides where requestedAt falls on that calendar date (UTC)
     */
    List<AdminRideView> listRides(String status, LocalDate date, int page, int size);

    Optional<AdminRideView> findRideById(UUID rideId);

    RideStats countStats();
}
