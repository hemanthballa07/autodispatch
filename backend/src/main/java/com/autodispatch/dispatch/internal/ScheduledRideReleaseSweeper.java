package com.autodispatch.dispatch.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Periodically releases SCHEDULED rides whose {@code scheduled_for} time has
 * passed, transitioning them to REQUESTED and triggering normal dispatch.
 *
 * Multi-instance safe: each ride is claimed via {@link DispatchService#releaseScheduledOne}
 * using a conditional update (affected-rows pattern); a losing instance matches
 * zero rows and skips. No Redis locks required.
 *
 * Scheduling is enabled by SchedulingConfig (autodispatch.sweeper.enabled,
 * default true; disabled in tests).
 */
@Component
class ScheduledRideReleaseSweeper {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRideReleaseSweeper.class);

    private final RideRepository rideRepository;
    private final DispatchService dispatchService;

    ScheduledRideReleaseSweeper(RideRepository rideRepository, DispatchService dispatchService) {
        this.rideRepository = rideRepository;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelay = 30_000)
    void sweep() {
        for (UUID rideId : rideRepository.findScheduledReadyForRelease()) {
            try {
                if (dispatchService.releaseScheduledOne(rideId)) {
                    dispatchService.startDispatch(rideId);
                }
            } catch (Exception e) {
                log.error("Scheduled release failed for ride {}", rideId, e);
            }
        }
    }
}
