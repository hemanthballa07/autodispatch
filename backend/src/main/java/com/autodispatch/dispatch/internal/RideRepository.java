package com.autodispatch.dispatch.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRepository extends JpaRepository<Ride, UUID> {

    boolean existsByRiderIdAndStatusIn(UUID riderId, Collection<RideStatus> statuses);

    Page<Ride> findByRiderId(UUID riderId, Pageable pageable);

    /**
     * Atomic claim: assigns the driver iff the ride is still BROADCASTING.
     * Concurrent claimers race on the conditional WHERE; exactly one wins.
     * Mirrors the legal BROADCASTING→ASSIGNED edge of the state machine.
     *
     * @return affected row count (1 = claimed, 0 = lost the race / wrong state)
     */
    @Modifying
    @Query(value = """
            UPDATE rides
               SET driver_id = :driverId,
                   status = 'ASSIGNED',
                   assigned_at = now(),
                   version = version + 1
             WHERE id = :rideId
               AND status = 'BROADCASTING'
            """, nativeQuery = true)
    int claimRide(@Param("rideId") UUID rideId, @Param("driverId") UUID driverId);

    /**
     * Compensation for handleDriverAccept: the claim won but the driver could
     * not be marked ON_RIDE. Reverts ASSIGNED→BROADCASTING for this driver
     * only and nulls the assignment.
     */
    @Modifying
    @Query(value = """
            UPDATE rides
               SET status = 'BROADCASTING',
                   driver_id = NULL,
                   assigned_at = NULL,
                   version = version + 1
             WHERE id = :rideId
               AND status = 'ASSIGNED'
               AND driver_id = :driverId
            """, nativeQuery = true)
    int revertClaim(@Param("rideId") UUID rideId, @Param("driverId") UUID driverId);

    /**
     * Terminal outcome when the driver-cancel rebroadcast budget is exhausted.
     * The locked matrix has no ASSIGNED→EXPIRED edge; the spec'd outcome is
     * applied with the same conditional-update pattern as claimRide.
     */
    @Modifying
    @Query(value = """
            UPDATE rides
               SET status = 'EXPIRED',
                   driver_id = NULL,
                   assigned_at = NULL,
                   current_round_expires_at = NULL,
                   version = version + 1
             WHERE id = :rideId
               AND status = 'ASSIGNED'
               AND driver_id = :driverId
            """, nativeQuery = true)
    int expireAfterCancelExhausted(@Param("rideId") UUID rideId, @Param("driverId") UUID driverId);

    /** BROADCASTING rides whose current round has timed out. */
    @Query(value = """
            SELECT id FROM rides
             WHERE status = 'BROADCASTING'
               AND current_round_expires_at < now()
            """, nativeQuery = true)
    List<UUID> findSweepCandidateIds();

    /**
     * Sweeper claim, round-advance arm: one instance wins the conditional
     * update on current_round_expires_at and owns broadcasting the new round.
     */
    @Modifying
    @Query(value = """
            UPDATE rides
               SET broadcast_round = broadcast_round + 1,
                   current_round_expires_at = now() + make_interval(secs => CAST(:ttlSeconds AS double precision)),
                   version = version + 1
             WHERE id = :rideId
               AND status = 'BROADCASTING'
               AND current_round_expires_at < now()
               AND broadcast_round < :maxRounds
            """, nativeQuery = true)
    int advanceRound(@Param("rideId") UUID rideId,
                     @Param("maxRounds") int maxRounds,
                     @Param("ttlSeconds") long ttlSeconds);

    /**
     * Sweeper claim, final-expiry arm (round budget spent). Encodes the legal
     * BROADCASTING→EXPIRED edge as an atomic conditional update.
     */
    @Modifying
    @Query(value = """
            UPDATE rides
               SET status = 'EXPIRED',
                   current_round_expires_at = NULL,
                   version = version + 1
             WHERE id = :rideId
               AND status = 'BROADCASTING'
               AND current_round_expires_at < now()
               AND broadcast_round >= :maxRounds
            """, nativeQuery = true)
    int expireTimedOut(@Param("rideId") UUID rideId, @Param("maxRounds") int maxRounds);

    /** The driver's single active ride, if any. */
    @Query(value = """
            SELECT id FROM rides
             WHERE driver_id = :driverId
               AND status IN ('ASSIGNED', 'ARRIVED', 'IN_PROGRESS')
             ORDER BY requested_at DESC
             LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findActiveRideIdForDriver(@Param("driverId") UUID driverId);

    /** Rides still BROADCASTING on which this driver holds an unanswered offer. */
    @Query(value = """
            SELECT r.id FROM rides r
              JOIN ride_offers o ON o.ride_id = r.id
             WHERE o.driver_id = :driverId
               AND o.response IS NULL
               AND r.status = 'BROADCASTING'
            """, nativeQuery = true)
    List<UUID> findOpenOfferedRideIds(@Param("driverId") UUID driverId);

    /** SCHEDULED rides whose scheduled_for time has arrived and are ready for dispatch. */
    @Query(value = """
            SELECT id FROM rides
             WHERE status = 'SCHEDULED'
               AND scheduled_for <= now()
            """, nativeQuery = true)
    List<UUID> findScheduledReadyForRelease();

    /**
     * Atomic release: transitions one SCHEDULED ride to REQUESTED iff it is
     * still SCHEDULED and its scheduled_for has passed. Multi-instance safe;
     * exactly one caller wins per ride (affected-rows pattern).
     *
     * @return 1 if this caller won the transition, 0 otherwise
     */
    @Modifying
    @Query(value = """
            UPDATE rides
               SET status = 'REQUESTED',
                   version = version + 1
             WHERE id = :rideId
               AND status = 'SCHEDULED'
               AND scheduled_for <= now()
            """, nativeQuery = true)
    int claimScheduledRelease(@Param("rideId") UUID rideId);

    long countByStatusIn(Collection<RideStatus> statuses);

    long countByStatusAndCompletedAtAfter(RideStatus status, Instant after);
}
