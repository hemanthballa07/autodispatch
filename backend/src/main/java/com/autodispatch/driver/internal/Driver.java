package com.autodispatch.driver.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "whatsapp_id", nullable = false, unique = true, length = 32)
    private String whatsappId;

    @Column(name = "vehicle_no", nullable = false, length = 20)
    private String vehicleNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DriverStatus status;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private boolean suspended;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_inbound_at")
    private Instant lastInboundAt;

    protected Driver() {
        // JPA only
    }

    public Driver(String name, String whatsappId, String vehicleNo, DriverStatus status, boolean verified) {
        this.name = name;
        this.whatsappId = whatsappId;
        this.vehicleNo = vehicleNo;
        this.status = status;
        this.verified = verified;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWhatsappId() {
        return whatsappId;
    }

    public String getVehicleNo() {
        return vehicleNo;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastInboundAt() {
        return lastInboundAt;
    }
}
