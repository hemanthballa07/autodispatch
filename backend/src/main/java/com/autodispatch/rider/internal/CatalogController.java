package com.autodispatch.rider.internal;

import com.autodispatch.fare.api.FareService;
import com.autodispatch.fare.api.LocationCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Locations and fare estimates for the booking UI. Talks to the fare module
 * through its public API only.
 */
@RestController
@RequestMapping("/api/v1")
class CatalogController {

    record LocationResponse(UUID id, String name, String zone) {
    }

    record FareEstimateResponse(BigDecimal amount) {
    }

    private final LocationCatalog locationCatalog;
    private final FareService fareService;

    CatalogController(LocationCatalog locationCatalog, FareService fareService) {
        this.locationCatalog = locationCatalog;
        this.fareService = fareService;
    }

    @GetMapping("/locations")
    List<LocationResponse> locations() {
        return locationCatalog.activeLocations().stream()
                .map(l -> new LocationResponse(l.id(), l.name(), l.zone()))
                .toList();
    }

    @GetMapping("/fares/estimate")
    FareEstimateResponse estimate(@RequestParam UUID pickupId,
                                  @RequestParam UUID dropId,
                                  @RequestParam(required = false) UUID vehicleTypeId) {
        BigDecimal amount = fareService.estimate(pickupId, dropId, vehicleTypeId)
                .orElseThrow(() -> new ApiExceptions.UnprocessableException(
                        "No fare rule covers this pickup/drop pair."));
        return new FareEstimateResponse(amount);
    }
}
