package com.autodispatch.safety.internal;

import com.autodispatch.safety.api.SafetyEventView;
import com.autodispatch.safety.api.SafetyService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
class DefaultSafetyService implements SafetyService {

    private final SafetyEventRepository repo;
    private final JdbcTemplate jdbc;

    DefaultSafetyService(SafetyEventRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @Override
    public SafetyEventView triggerSos(UUID rideId, UUID riderId, String details) {
        validateRiderOwnsRide(rideId, riderId);
        return toView(repo.save(new SafetyEvent(rideId, riderId, "SOS", details)));
    }

    @Override
    public SafetyEventView reportIncident(UUID rideId, UUID riderId, String details) {
        validateRiderOwnsRide(rideId, riderId);
        return toView(repo.save(new SafetyEvent(rideId, riderId, "INCIDENT_REPORT", details)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SafetyEventView> findByRide(UUID rideId) {
        return repo.findByRideIdOrderByCreatedAtDesc(rideId).stream().map(this::toView).toList();
    }

    private void validateRiderOwnsRide(UUID rideId, UUID riderId) {
        UUID rideRiderId;
        try {
            rideRiderId = jdbc.queryForObject("SELECT rider_id FROM rides WHERE id = ?", UUID.class, rideId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Ride not found: " + rideId);
        }
        if (!riderId.equals(rideRiderId)) {
            throw new IllegalArgumentException("Ride " + rideId + " not owned by rider: " + riderId);
        }
    }

    private SafetyEventView toView(SafetyEvent e) {
        return new SafetyEventView(e.getId(), e.getRideId(), e.getRiderId(),
                e.getType(), e.getDetails(), e.getCreatedAt());
    }
}
