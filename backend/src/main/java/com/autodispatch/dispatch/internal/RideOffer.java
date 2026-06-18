package com.autodispatch.dispatch.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One offer sent to one driver for one ride. unique(ride_id, driver_id) is
 * enforced by the database.
 */
@Entity
@Table(name = "ride_offers")
public class RideOffer {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private OfferResponse response;

    @Column(nullable = false)
    private int round;

    protected RideOffer() {
        // JPA only
    }

    public RideOffer(UUID rideId, UUID driverId, int round) {
        this.rideId = rideId;
        this.driverId = driverId;
        this.round = round;
    }

    /**
     * Re-offers this ride to the same driver in a later round
     * (unique(ride_id, driver_id) forbids a second row).
     */
    public void reissue(int round) {
        this.round = round;
        this.sentAt = Instant.now();
        this.respondedAt = null;
        this.response = null;
    }

    @PrePersist
    void onCreate() {
        if (sentAt == null) {
            sentAt = Instant.now();
        }
    }

    public void respond(OfferResponse response) {
        this.response = response;
        this.respondedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRideId() {
        return rideId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public OfferResponse getResponse() {
        return response;
    }

    public int getRound() {
        return round;
    }
}
