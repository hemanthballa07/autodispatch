package com.autodispatch.dispatch.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ride aggregate. All status writes go through {@link #transitionTo}; the only
 * exceptions are the atomic conditional updates in {@link RideRepository},
 * which encode the same legal edges in SQL (BROADCASTING→ASSIGNED).
 *
 * Cross-module references (rider, driver) are held as plain UUIDs — entity
 * associations across module boundaries are forbidden by the architecture.
 */
@Entity
@Table(name = "rides")
public class Ride {

    /** Max times a ride may return ASSIGNED→BROADCASTING after a driver cancel. */
    static final int MAX_REBROADCASTS = 1;

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "pickup_label", nullable = false, length = 120)
    private String pickupLabel;

    @Column(name = "drop_label", nullable = false, length = 120)
    private String dropLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RideStatus status;

    @Column(name = "fare_amount", precision = 10, scale = 2)
    private BigDecimal fareAmount;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "cancelled_by", length = 16)
    private String cancelledBy;

    @Column(name = "vehicle_type_id")
    private UUID vehicleTypeId;

    @Column(name = "pickup_location_id")
    private UUID pickupLocationId;

    @Column(name = "drop_location_id")
    private UUID dropLocationId;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "final_amount", precision = 10, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "rebroadcast_count", nullable = false)
    private int rebroadcastCount;

    @Column(name = "broadcast_round", nullable = false)
    private int broadcastRound;

    @Column(name = "current_round_expires_at")
    private Instant currentRoundExpiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Ride() {
        // JPA only
    }

    public Ride(UUID riderId, String pickupLabel, String dropLabel) {
        this.riderId = riderId;
        this.pickupLabel = pickupLabel;
        this.dropLabel = dropLabel;
        this.status = RideStatus.REQUESTED;
        this.rebroadcastCount = 0;
    }

    /** Booking constructor: the fare quote is captured here and never changes. */
    public Ride(UUID riderId, String pickupLabel, String dropLabel, BigDecimal quotedFare) {
        this(riderId, pickupLabel, dropLabel);
        this.fareAmount = quotedFare;
    }

    /**
     * Scheduled-ride constructor: ride starts in SCHEDULED state and is released
     * to REQUESTED by {@code ScheduledRideReleaseSweeper} at {@code scheduledFor}.
     */
    public Ride(UUID riderId, String pickupLabel, String dropLabel, BigDecimal quotedFare,
                UUID vehicleTypeId, UUID pickupLocationId, UUID dropLocationId, Instant scheduledFor) {
        this(riderId, pickupLabel, dropLabel, quotedFare);
        this.vehicleTypeId = vehicleTypeId;
        this.pickupLocationId = pickupLocationId;
        this.dropLocationId = dropLocationId;
        if (scheduledFor != null) {
            this.status = RideStatus.SCHEDULED;
            this.scheduledFor = scheduledFor;
        }
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    /**
     * The single legal way to change ride status. Throws
     * {@link IllegalRideTransitionException} on any edge not in the
     * allowed-transitions map, and on ASSIGNED→BROADCASTING once the
     * rebroadcast budget ({@value MAX_REBROADCASTS}) is spent.
     */
    public void transitionTo(RideStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalRideTransitionException(id, status, newStatus);
        }
        if (status == RideStatus.ASSIGNED && newStatus == RideStatus.BROADCASTING) {
            if (rebroadcastCount >= MAX_REBROADCASTS) {
                throw new IllegalRideTransitionException(id, status, newStatus,
                        "rebroadcast budget (" + MAX_REBROADCASTS + ") exhausted");
            }
            rebroadcastCount++;
        }
        if (newStatus == RideStatus.ARRIVED) {
            arrivedAt = Instant.now();
        }
        if (newStatus == RideStatus.IN_PROGRESS) {
            startedAt = Instant.now();
        }
        if (newStatus == RideStatus.COMPLETED) {
            completedAt = Instant.now();
        }
        setStatus(newStatus);
    }

    private void setStatus(RideStatus newStatus) {
        this.status = newStatus;
    }

    /** Starts the next broadcast round: bumps the round counter and sets its expiry. */
    public void beginRound(Instant roundExpiresAt) {
        this.broadcastRound++;
        this.currentRoundExpiresAt = roundExpiresAt;
    }

    /** Clears the driver assignment (driver-cancel rebroadcast path). */
    public void clearAssignment() {
        this.driverId = null;
        this.assignedAt = null;
    }

    public void recordCancelReason(String reason) {
        this.cancelReason = reason;
    }

    public void recordCancelledBy(String party) {
        this.cancelledBy = party;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRiderId() {
        return riderId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public String getPickupLabel() {
        return pickupLabel;
    }

    public String getDropLabel() {
        return dropLabel;
    }

    public RideStatus getStatus() {
        return status;
    }

    public BigDecimal getFareAmount() {
        return fareAmount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public UUID getVehicleTypeId() {
        return vehicleTypeId;
    }

    public UUID getPickupLocationId() {
        return pickupLocationId;
    }

    public UUID getDropLocationId() {
        return dropLocationId;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public int getRebroadcastCount() {
        return rebroadcastCount;
    }

    public int getBroadcastRound() {
        return broadcastRound;
    }

    public Instant getCurrentRoundExpiresAt() {
        return currentRoundExpiresAt;
    }

    public Long getVersion() {
        return version;
    }
}
