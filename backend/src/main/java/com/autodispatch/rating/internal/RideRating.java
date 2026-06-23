package com.autodispatch.rating.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ride_ratings")
class RideRating {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "rater_rider_id", nullable = false)
    private UUID raterRiderId;

    @Column(name = "driver_stars", nullable = false)
    private int driverStars;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RideRating() {
        // JPA only
    }

    RideRating(UUID rideId, UUID driverId, UUID raterRiderId, int driverStars, String comment) {
        this.rideId = rideId;
        this.driverId = driverId;
        this.raterRiderId = raterRiderId;
        this.driverStars = driverStars;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getRideId() { return rideId; }
    UUID getDriverId() { return driverId; }
    UUID getRaterRiderId() { return raterRiderId; }
    int getDriverStars() { return driverStars; }
    String getComment() { return comment; }
    Instant getCreatedAt() { return createdAt; }
}
