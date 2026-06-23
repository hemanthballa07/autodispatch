package com.autodispatch.rider.internal;

import com.autodispatch.dispatch.api.DispatchApi;
import com.autodispatch.dispatch.api.RideBooking;
import com.autodispatch.dispatch.api.RideView;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.fare.api.FareService;
import com.autodispatch.fare.api.LocationCatalog;
import com.autodispatch.fare.api.LocationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Rider ride endpoints. Dispatch is reached ONLY through its public API;
 * ownership checks return 404 (never 403) so ride existence does not leak.
 */
@RestController
@RequestMapping("/api/v1/rides")
class RideController {

    private static final int HISTORY_PAGE_SIZE = 20;
    private static final Set<String> CANCELLABLE = Set.of("SCHEDULED", "REQUESTED", "BROADCASTING", "ASSIGNED");

    record CreateRideRequest(@NotNull UUID pickupId, @NotNull UUID dropId,
                             UUID vehicleTypeId, Instant scheduledFor) {
    }

    record DriverCard(String name, String vehicleNo, String maskedPhone) {
    }

    record RideResponse(UUID id, String status, String pickup, String drop, BigDecimal fare,
                        DriverCard driver, boolean cancellable, Instant requestedAt,
                        Instant completedAt, String cancelReason, Instant scheduledFor) {
    }

    private final RideBooking rideBooking;
    private final DispatchApi dispatchApi;
    private final LocationCatalog locationCatalog;
    private final FareService fareService;
    private final DriverAvailabilityService driverAvailability;
    private final RideCreationRateLimiter rateLimiter;

    RideController(RideBooking rideBooking,
                   DispatchApi dispatchApi,
                   LocationCatalog locationCatalog,
                   FareService fareService,
                   DriverAvailabilityService driverAvailability,
                   RideCreationRateLimiter rateLimiter) {
        this.rideBooking = rideBooking;
        this.dispatchApi = dispatchApi;
        this.locationCatalog = locationCatalog;
        this.fareService = fareService;
        this.driverAvailability = driverAvailability;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RideResponse create(@Valid @RequestBody CreateRideRequest request,
                        @RequestAttribute(RiderAuthFilter.RIDER_ID_ATTRIBUTE) UUID riderId) {
        if (rateLimiter.isLimited(riderId)) {
            throw new ApiExceptions.RateLimitedException(
                    "Ride creation limit reached (%d per %d minutes). Please wait a bit."
                            .formatted(RideCreationRateLimiter.MAX_CREATIONS,
                                    RideCreationRateLimiter.WINDOW.toMinutes()));
        }
        LocationView pickup = activeLocation(request.pickupId(), "pickup");
        LocationView drop = activeLocation(request.dropId(), "drop");
        BigDecimal fare = fareService.estimate(pickup.id(), drop.id(), request.vehicleTypeId())
                .orElseThrow(() -> new ApiExceptions.UnprocessableException(
                        "No fare rule covers this pickup/drop pair."));

        UUID rideId = rideBooking.requestRide(riderId, pickup.name(), drop.name(), fare,
                request.vehicleTypeId(), request.pickupId(), request.dropId(), request.scheduledFor());
        rateLimiter.recordCreation(riderId);
        if (request.scheduledFor() == null) {
            dispatchApi.startDispatch(rideId);
        }
        return toResponse(ownRide(rideId, riderId));
    }

    @GetMapping("/{rideId}")
    RideResponse get(@PathVariable UUID rideId,
                     @RequestAttribute(RiderAuthFilter.RIDER_ID_ATTRIBUTE) UUID riderId) {
        return toResponse(ownRide(rideId, riderId));
    }

    @PostMapping("/{rideId}/cancel")
    RideResponse cancel(@PathVariable UUID rideId,
                        @RequestAttribute(RiderAuthFilter.RIDER_ID_ATTRIBUTE) UUID riderId) {
        ownRide(rideId, riderId);
        rideBooking.cancelByRider(rideId);
        return toResponse(ownRide(rideId, riderId));
    }

    @GetMapping
    List<RideResponse> history(@RequestParam(name = "mine", defaultValue = "true") boolean mine,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestAttribute(RiderAuthFilter.RIDER_ID_ATTRIBUTE) UUID riderId) {
        if (!mine) {
            throw new IllegalArgumentException("Only mine=true is supported.");
        }
        return rideBooking.ridesForRider(riderId, Math.max(page, 0), HISTORY_PAGE_SIZE).stream()
                .map(this::toResponse)
                .toList();
    }

    private LocationView activeLocation(UUID id, String role) {
        return locationCatalog.findById(id)
                .filter(LocationView::active)
                .orElseThrow(() -> new ApiExceptions.UnprocessableException(
                        "Unknown or inactive %s location.".formatted(role)));
    }

    private RideView ownRide(UUID rideId, UUID riderId) {
        return rideBooking.findRide(rideId)
                .filter(view -> view.riderId().equals(riderId))
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Ride not found."));
    }

    private RideResponse toResponse(RideView view) {
        DriverCard driver = null;
        if (view.driverId() != null) {
            driver = driverAvailability.findById(view.driverId())
                    .map(d -> new DriverCard(d.name(), d.vehicleNo(), maskPhone(d.whatsappId())))
                    .orElse(null);
        }
        return new RideResponse(view.id(), view.status(), view.pickupLabel(), view.dropLabel(),
                view.fareAmount(), driver, CANCELLABLE.contains(view.status()),
                view.requestedAt(), view.completedAt(), view.cancelReason(), view.scheduledFor());
    }

    /** Never expose the raw wa_id: keep the country code and last 4 digits. */
    private static String maskPhone(String phoneE164) {
        if (phoneE164 == null || phoneE164.length() < 8) {
            return "••••";
        }
        return phoneE164.substring(0, 3)
                + "•".repeat(phoneE164.length() - 7)
                + phoneE164.substring(phoneE164.length() - 4);
    }
}
