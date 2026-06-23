package com.autodispatch.payment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
class PaymentTransaction {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 16)
    private String method;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentTransaction() {
        // JPA only
    }

    PaymentTransaction(UUID rideId, UUID riderId, String type, BigDecimal amount) {
        this.rideId = rideId;
        this.riderId = riderId;
        this.type = type;
        this.amount = amount;
        this.method = "CASH";
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void markCollected() {
        this.status = "COLLECTED";
        this.acknowledgedAt = Instant.now();
        this.updatedAt = this.acknowledgedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getRideId() {
        return rideId;
    }

    UUID getRiderId() {
        return riderId;
    }

    String getType() {
        return type;
    }

    String getMethod() {
        return method;
    }

    String getStatus() {
        return status;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
