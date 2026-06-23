package com.autodispatch.rating.internal;

import com.autodispatch.rating.api.DriverRatingStats;
import com.autodispatch.rating.api.RatingService;
import com.autodispatch.rating.api.RatingView;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
class DefaultRatingService implements RatingService {

    private final RideRatingRepository repo;
    private final JdbcTemplate jdbc;

    DefaultRatingService(RideRatingRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @Override
    public RatingView rateDriver(UUID rideId, UUID riderIdFromAuth, int stars, String comment) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Stars must be between 1 and 5");
        }
        Map<String, Object> ride;
        try {
            ride = jdbc.queryForMap("SELECT status, rider_id, driver_id FROM rides WHERE id = ?", rideId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Ride not found: " + rideId);
        }
        if (!"COMPLETED".equals(ride.get("status"))) {
            throw new IllegalArgumentException("Ride " + rideId + " is not COMPLETED");
        }
        if (!riderIdFromAuth.equals(ride.get("rider_id"))) {
            throw new IllegalArgumentException("Ride " + rideId + " not owned by rider: " + riderIdFromAuth);
        }
        UUID driverId = (UUID) ride.get("driver_id");
        if (driverId == null) {
            throw new IllegalArgumentException("Ride " + rideId + " has no assigned driver");
        }
        return toView(repo.save(new RideRating(rideId, driverId, riderIdFromAuth, stars, comment)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RatingView> findByRideAndRider(UUID rideId, UUID riderId) {
        return repo.findByRideIdAndRaterRiderId(rideId, riderId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverRatingStats getDriverStats(UUID driverId) {
        Double avg = repo.findAverageStarsByDriverId(driverId);
        long count = repo.countByDriverId(driverId);
        return new DriverRatingStats(driverId,
                avg != null ? BigDecimal.valueOf(avg) : BigDecimal.ZERO,
                count);
    }

    private RatingView toView(RideRating r) {
        return new RatingView(r.getId(), r.getRideId(), r.getDriverId(), r.getRaterRiderId(),
                r.getDriverStars(), r.getComment(), r.getCreatedAt());
    }
}
