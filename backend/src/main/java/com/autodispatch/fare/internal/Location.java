package com.autodispatch.fare.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String zone;

    @Column(nullable = false)
    private boolean active;

    protected Location() {
        // JPA only
    }

    public Location(String name, String zone, boolean active) {
        this.name = name;
        this.zone = zone;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getZone() {
        return zone;
    }

    public boolean isActive() {
        return active;
    }
}
