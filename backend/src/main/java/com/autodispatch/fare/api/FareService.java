package com.autodispatch.fare.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface FareService {

    /**
     * Fare quote for a trip between two active locations.
     *
     * @return empty when either location is unknown/inactive or no fare rule
     *         covers the zone pair (callers map this to HTTP 422)
     */
    Optional<BigDecimal> estimate(UUID pickupLocationId, UUID dropLocationId);
}
