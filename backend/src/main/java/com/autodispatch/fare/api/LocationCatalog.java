package com.autodispatch.fare.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationCatalog {

    List<LocationView> activeLocations();

    Optional<LocationView> findById(UUID locationId);
}
