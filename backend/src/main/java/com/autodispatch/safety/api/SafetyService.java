package com.autodispatch.safety.api;

import java.util.List;
import java.util.UUID;

public interface SafetyService {
    SafetyEventView triggerSos(UUID rideId, UUID riderId, String details);
    SafetyEventView reportIncident(UUID rideId, UUID riderId, String details);
    List<SafetyEventView> findByRide(UUID rideId);
}
