package com.autodispatch.safety.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_events")
class SafetyEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SafetyEvent() {
        // JPA only
    }

    SafetyEvent(UUID rideId, UUID riderId, String type, String details) {
        this.rideId = rideId;
        this.riderId = riderId;
        this.type = type;
        this.details = details;
        this.createdAt = Instant.now();
    }

    UUID getId() { return id; }
    UUID getRideId() { return rideId; }
    UUID getRiderId() { return riderId; }
    String getType() { return type; }
    String getDetails() { return details; }
    Instant getCreatedAt() { return createdAt; }
}
