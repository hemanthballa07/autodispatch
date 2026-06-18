package com.autodispatch.rider.api;

import java.util.Optional;
import java.util.UUID;

public interface RiderLookup {

    Optional<RiderSummary> findById(UUID riderId);
}
