package com.autodispatch.dispatch.internal;

import com.autodispatch.common.api.RideCodes;
import com.autodispatch.dispatch.api.DispatchApi;
import com.autodispatch.dispatch.api.DispatchQueries;
import com.autodispatch.dispatch.api.DriverNotEligibleException;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.driver.api.DriverSummary;
import com.autodispatch.notification.api.MessageCatalog;
import com.autodispatch.notification.api.RideOfferNotification;
import com.autodispatch.notification.api.WhatsAppGateway;
import com.autodispatch.rider.api.RiderLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispatch orchestration. Talks to other modules strictly through their api
 * packages (driver availability, rider lookup, WhatsApp gateway). All ride
 * status changes go through {@link Ride#transitionTo} or the atomic
 * conditional updates in {@link RideRepository} — never raw status writes.
 */
@Service
public class DispatchService implements DispatchApi, DispatchQueries {

    static final Duration ROUND_TTL = Duration.ofSeconds(45);
    static final int MAX_ROUNDS = 3;

    /** WhatsApp 24h session window: offers only reach drivers with a recent inbound. */
    static final Duration SESSION_WINDOW = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final RideRepository rideRepository;
    private final RideOfferRepository offerRepository;
    private final DriverAvailabilityService driverAvailability;
    private final RiderLookup riderLookup;
    private final WhatsAppGateway whatsAppGateway;

    public DispatchService(RideRepository rideRepository,
                           RideOfferRepository offerRepository,
                           DriverAvailabilityService driverAvailability,
                           RiderLookup riderLookup,
                           WhatsAppGateway whatsAppGateway) {
        this.rideRepository = rideRepository;
        this.offerRepository = offerRepository;
        this.driverAvailability = driverAvailability;
        this.riderLookup = riderLookup;
        this.whatsAppGateway = whatsAppGateway;
    }

    @Override
    @Transactional
    public void startDispatch(UUID rideId) {
        Ride ride = require(rideId);
        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new IllegalRideTransitionException(rideId, ride.getStatus(),
                    RideStatus.BROADCASTING, "startDispatch requires a REQUESTED ride");
        }

        List<DriverSummary> available = driverAvailability.listAvailableVerified();
        if (available.isEmpty()) {
            // REQUESTED→EXPIRED is not a legal edge of the locked state machine;
            // the legal path to the spec'd outcome is the two-step
            // REQUESTED→BROADCASTING→EXPIRED within this one transaction.
            ride.transitionTo(RideStatus.BROADCASTING);
            ride.transitionTo(RideStatus.EXPIRED);
            notifyRider(ride, MessageCatalog.noDrivers());
            return;
        }

        ride.beginRound(Instant.now().plus(ROUND_TTL));
        ride.transitionTo(RideStatus.BROADCASTING);
        sendOffers(ride, available);
    }

    @Override
    @Transactional
    public void handleDriverAccept(String driverWhatsappId, UUID rideId) {
        DriverSummary driver = driverAvailability.findByWhatsappId(driverWhatsappId)
                .orElseThrow(() -> new DriverNotEligibleException(
                        "Unknown driver whatsapp id: " + driverWhatsappId));
        if (!driver.verified()) {
            throw new DriverNotEligibleException("Driver %s is not verified".formatted(driver.id()));
        }

        int claimed = rideRepository.claimRide(rideId, driver.id());
        if (claimed == 0) {
            respondToOffer(rideId, driver.id(), OfferResponse.TOO_LATE);
            whatsAppGateway.sendText(driver.whatsappId(), MessageCatalog.alreadyTaken());
            return;
        }

        if (!driverAvailability.tryMarkOnRide(driver.id())) {
            // COMPENSATE: the ride was claimed but the driver could not be put
            // ON_RIDE (grabbed elsewhere or went offline). Direct conditional
            // revert ASSIGNED→BROADCASTING for this driver only.
            int reverted = rideRepository.revertClaim(rideId, driver.id());
            log.error("COMPENSATION: ride {} was claimed by driver {} but markOnRide failed; "
                            + "revertClaim affected {} row(s) — ride returned to BROADCASTING",
                    rideId, driver.id(), reverted);
            return;
        }

        respondToOffer(rideId, driver.id(), OfferResponse.ACCEPTED);

        Ride ride = require(rideId);
        notifyRider(ride, MessageCatalog.driverAssigned(
                driver.name(), driver.vehicleNo(), ride.getPickupLabel()));
        for (RideOffer other : offerRepository.findByRideId(rideId)) {
            if (!other.getDriverId().equals(driver.id()) && other.getResponse() == null) {
                driverAvailability.findById(other.getDriverId()).ifPresent(losing ->
                        whatsAppGateway.sendText(losing.whatsappId(), MessageCatalog.alreadyTaken()));
            }
        }
    }

    @Override
    @Transactional
    public void handleDriverCancel(UUID rideId) {
        Ride ride = require(rideId);
        UUID cancellingDriverId = ride.getDriverId();
        if (ride.getStatus() != RideStatus.ASSIGNED || cancellingDriverId == null) {
            throw new IllegalRideTransitionException(rideId, ride.getStatus(),
                    RideStatus.BROADCASTING, "driver-cancel requires an ASSIGNED ride with a driver");
        }

        try {
            ride.transitionTo(RideStatus.BROADCASTING); // guarded: max 1 rebroadcast
        } catch (IllegalRideTransitionException guardExhausted) {
            // The locked matrix has no ASSIGNED→EXPIRED edge; the spec'd
            // terminal outcome is applied via a conditional repository update
            // (same sanctioned pattern as claimRide/revertClaim).
            rideRepository.expireAfterCancelExhausted(rideId, cancellingDriverId);
            driverAvailability.makeAvailable(cancellingDriverId);
            notifyRider(ride, MessageCatalog.rideExpired());
            return;
        }

        ride.clearAssignment();
        ride.beginRound(Instant.now().plus(ROUND_TTL));
        driverAvailability.makeAvailable(cancellingDriverId);
        sendOffers(ride, driverAvailability.listAvailableVerified());
    }

    @Override
    @Transactional
    public void markArrived(UUID rideId) {
        require(rideId).transitionTo(RideStatus.ARRIVED);
    }

    @Override
    @Transactional
    public void markStarted(UUID rideId) {
        require(rideId).transitionTo(RideStatus.IN_PROGRESS);
    }

    @Override
    @Transactional
    public void markCompleted(UUID rideId) {
        Ride ride = require(rideId);
        UUID driverId = ride.getDriverId();
        ride.transitionTo(RideStatus.COMPLETED);
        if (driverId != null) {
            driverAvailability.makeAvailable(driverId);
        }
    }

    /**
     * Atomic SCHEDULED→REQUESTED release for one ride. Multi-instance safe:
     * the conditional update ensures exactly one caller wins per ride.
     *
     * @return true if this caller won and the ride is now REQUESTED
     */
    @Transactional
    boolean releaseScheduledOne(UUID rideId) {
        return rideRepository.claimScheduledRelease(rideId) == 1;
    }

    /**
     * One sweep step for one ride, multi-instance safe: ownership of the round
     * advance / final expiry is claimed via conditional updates on
     * current_round_expires_at — affected-rows pattern, no Redis locks.
     */
    @Transactional
    public void sweepOne(UUID rideId) {
        if (rideRepository.advanceRound(rideId, MAX_ROUNDS, ROUND_TTL.toSeconds()) == 1) {
            Ride ride = require(rideId);
            sendOffers(ride, driverAvailability.listAvailableVerified());
            return;
        }
        if (rideRepository.expireTimedOut(rideId, MAX_ROUNDS) == 1) {
            for (RideOffer offer : offerRepository.findByRideId(rideId)) {
                if (offer.getResponse() == null) {
                    offer.respond(OfferResponse.IGNORED);
                }
            }
            notifyRider(require(rideId), MessageCatalog.rideExpired());
        }
        // else: another instance handled it, or an accept landed first — nothing to do.
    }

    /**
     * Sends offers for the ride's current round to the given drivers, skipping
     * drivers who already responded ACCEPTED or TOO_LATE for this ride.
     * Existing offer rows are reissued (unique(ride_id, driver_id)).
     */
    private void sendOffers(Ride ride, List<DriverSummary> drivers) {
        Map<UUID, RideOffer> existing = offerRepository.findByRideId(ride.getId()).stream()
                .collect(Collectors.toMap(RideOffer::getDriverId, Function.identity()));

        RideOfferNotification notification = new RideOfferNotification(
                ride.getId(), ride.getPickupLabel(), ride.getDropLabel(), ride.getFareAmount(),
                RideCodes.codeFor(ride.getId()), ROUND_TTL.toSeconds());
        Instant sessionCutoff = Instant.now().minus(SESSION_WINDOW);

        for (DriverSummary driver : drivers) {
            // WhatsApp 24h session rule (channel constraint; template fallback
            // is a config-flag stub, intentionally not implemented).
            if (driver.lastInboundAt() == null || driver.lastInboundAt().isBefore(sessionCutoff)) {
                continue;
            }
            RideOffer offer = existing.get(driver.id());
            if (offer != null && (offer.getResponse() == OfferResponse.ACCEPTED
                    || offer.getResponse() == OfferResponse.TOO_LATE)) {
                continue;
            }
            if (offer == null) {
                offerRepository.save(new RideOffer(ride.getId(), driver.id(), ride.getBroadcastRound()));
            } else {
                offer.reissue(ride.getBroadcastRound());
            }
            whatsAppGateway.sendRideOffer(driver.whatsappId(), notification);
        }
    }

    // ---- DispatchQueries (read side for adapters) ----

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findActiveRideForDriver(UUID driverId) {
        return rideRepository.findActiveRideIdForDriver(driverId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> resolveOpenOfferByCode(UUID driverId, String rideCode) {
        return rideRepository.findOpenOfferedRideIds(driverId).stream()
                .filter(rideId -> RideCodes.matches(rideCode, rideId))
                .findFirst();
    }

    private void respondToOffer(UUID rideId, UUID driverId, OfferResponse response) {
        offerRepository.findByRideIdAndDriverId(rideId, driverId).ifPresent(offer -> {
            if (offer.getResponse() == null) {
                offer.respond(response);
            }
        });
    }

    private void notifyRider(Ride ride, String text) {
        riderLookup.findById(ride.getRiderId())
                .ifPresent(rider -> whatsAppGateway.sendText(rider.phone(), text));
    }

    private Ride require(UUID rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ride: " + rideId));
    }
}
