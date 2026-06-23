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
@Table(name = "driver_ledger")
class DriverLedgerEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "ride_id")
    private UUID rideId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DriverLedgerEntry() {
        // JPA only
    }

    DriverLedgerEntry(UUID driverId, UUID rideId, String type, BigDecimal amount, String note) {
        this.driverId = driverId;
        this.rideId = rideId;
        this.type = type;
        this.amount = amount;
        this.note = note;
        this.createdAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getDriverId() {
        return driverId;
    }

    UUID getRideId() {
        return rideId;
    }

    String getType() {
        return type;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getNote() {
        return note;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
