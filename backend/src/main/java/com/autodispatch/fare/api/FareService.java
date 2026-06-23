package com.autodispatch.fare.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface FareService {

    /**
     * Fare quote for a trip between two active locations.
     * When {@code vehicleTypeId} is null the cheapest (first) fare rule for
     * the zone pair is returned — valid while only one vehicle type exists.
     *
     * @return empty when either location is unknown/inactive or no fare rule
     *         covers the zone pair (callers map this to HTTP 422)
     */
    Optional<BigDecimal> estimate(UUID pickupLocationId, UUID dropLocationId, UUID vehicleTypeId);

    /** Backward-compatible overload: delegates to {@link #estimate(UUID, UUID, UUID)} with null type. */
    default Optional<BigDecimal> estimate(UUID pickupLocationId, UUID dropLocationId) {
        return estimate(pickupLocationId, dropLocationId, null);
    }
}
