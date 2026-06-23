package com.autodispatch.safety.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SafetyEventRepository extends JpaRepository<SafetyEvent, UUID> {
    List<SafetyEvent> findByRideIdOrderByCreatedAtDesc(UUID rideId);
}
