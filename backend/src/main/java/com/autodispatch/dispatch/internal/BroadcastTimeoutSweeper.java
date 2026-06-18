package com.autodispatch.dispatch.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Periodically advances or expires BROADCASTING rides whose round has timed
 * out. Multi-instance safe: each ride is claimed inside
 * {@link DispatchService#sweepOne} via conditional updates (affected-rows
 * pattern); a losing instance simply matches zero rows. No Redis locks.
 *
 * Scheduling is enabled by SchedulingConfig (autodispatch.sweeper.enabled,
 * default true; disabled in tests, which call sweepOne directly).
 */
@Component
class BroadcastTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(BroadcastTimeoutSweeper.class);

    private final RideRepository rideRepository;
    private final DispatchService dispatchService;

    BroadcastTimeoutSweeper(RideRepository rideRepository, DispatchService dispatchService) {
        this.rideRepository = rideRepository;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelay = 10_000)
    void sweep() {
        for (UUID rideId : rideRepository.findSweepCandidateIds()) {
            try {
                dispatchService.sweepOne(rideId);
            } catch (Exception e) {
                log.error("Sweep failed for ride {}", rideId, e);
            }
        }
    }
}
