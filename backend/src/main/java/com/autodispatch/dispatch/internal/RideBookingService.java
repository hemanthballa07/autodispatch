package com.autodispatch.dispatch.internal;

import com.autodispatch.dispatch.api.ActiveRideExistsException;
import com.autodispatch.dispatch.api.RideBooking;
import com.autodispatch.dispatch.api.RideNotCancellableException;
import com.autodispatch.dispatch.api.RideView;
import java.time.Instant;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.notification.api.MessageCatalog;
import com.autodispatch.notification.api.WhatsAppGateway;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RideBookingService implements RideBooking {

    static final Set<RideStatus> ACTIVE_STATUSES = EnumSet.of(
            RideStatus.SCHEDULED, RideStatus.REQUESTED, RideStatus.BROADCASTING,
            RideStatus.ASSIGNED, RideStatus.ARRIVED, RideStatus.IN_PROGRESS);

    private final RideRepository rideRepository;
    private final DriverAvailabilityService driverAvailability;
    private final WhatsAppGateway whatsAppGateway;

    public RideBookingService(RideRepository rideRepository,
                              DriverAvailabilityService driverAvailability,
                              WhatsAppGateway whatsAppGateway) {
        this.rideRepository = rideRepository;
        this.driverAvailability = driverAvailability;
        this.whatsAppGateway = whatsAppGateway;
    }

    @Override
    @Transactional
    public UUID requestRide(UUID riderId, String pickupLabel, String dropLabel, BigDecimal quotedFare,
                            UUID vehicleTypeId, UUID pickupLocationId, UUID dropLocationId,
                            Instant scheduledFor) {
        if (rideRepository.existsByRiderIdAndStatusIn(riderId, ACTIVE_STATUSES)) {
            throw new ActiveRideExistsException(riderId);
        }
        try {
            // saveAndFlush so a concurrent double-tap hits the partial unique
            // index (one active ride per rider) inside this transaction.
            return rideRepository.saveAndFlush(
                    new Ride(riderId, pickupLabel, dropLabel, quotedFare,
                             vehicleTypeId, pickupLocationId, dropLocationId, scheduledFor)).getId();
        } catch (DataIntegrityViolationException raced) {
            throw new ActiveRideExistsException(riderId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RideView> findRide(UUID rideId) {
        return rideRepository.findById(rideId).map(RideBookingService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RideView> ridesForRider(UUID riderId, int page, int size) {
        return rideRepository.findByRiderId(riderId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt")))
                .stream()
                .map(RideBookingService::toView)
                .toList();
    }

    @Override
    @Transactional
    public void cancelByRider(UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ride: " + rideId));
        UUID assignedDriverId = ride.getDriverId();
        try {
            ride.transitionTo(RideStatus.CANCELLED);
        } catch (IllegalRideTransitionException notAllowed) {
            throw new RideNotCancellableException(rideId, ride.getStatus().name());
        }
        ride.recordCancelReason("Cancelled by rider");
        if (assignedDriverId != null) {
            driverAvailability.makeAvailable(assignedDriverId);
            driverAvailability.findById(assignedDriverId).ifPresent(driver ->
                    whatsAppGateway.sendText(driver.whatsappId(), MessageCatalog.rideCancelled()));
        }
    }

    private static RideView toView(Ride ride) {
        return new RideView(ride.getId(), ride.getRiderId(), ride.getDriverId(),
                ride.getPickupLabel(), ride.getDropLabel(), ride.getStatus().name(),
                ride.getFareAmount(), ride.getRequestedAt(), ride.getAssignedAt(),
                ride.getCompletedAt(), ride.getCancelReason(), ride.getScheduledFor());
    }
}
