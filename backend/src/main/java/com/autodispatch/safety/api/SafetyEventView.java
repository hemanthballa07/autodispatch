package com.autodispatch.safety.api;

import java.time.Instant;
import java.util.UUID;

public record SafetyEventView(UUID id, UUID rideId, UUID riderId, String type, String details, Instant createdAt) {}
