package com.autodispatch.rating.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.rating.api.DriverRatingStats;
import com.autodispatch.rating.api.RatingService;
import com.autodispatch.rating.api.RatingView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 10 / Phase 3 gate tests — rating service layer.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RatingServiceTest {

    @Autowired
    private RatingService ratingService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID riderId;
    private UUID driverId;
    private UUID rideId;

    @BeforeEach
    void seed() {
        riderId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        rideId = UUID.randomUUID();
        String phone = "+91" + (7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        String waId = "+91" + (9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        jdbc.update("INSERT INTO riders (id, name, phone, created_at) VALUES (?,?,?,now())",
                riderId, "Rating Rider", phone);
        jdbc.update("INSERT INTO drivers (id, name, whatsapp_id, vehicle_no, status, verified, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,now(),now())",
                driverId, "Rating Driver", waId, "KA-03-1234", "OFFLINE", true);
        jdbc.update("INSERT INTO rides (id, rider_id, driver_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                        + "VALUES (?,?,?,?,?,?,?,now())",
                rideId, riderId, driverId, "COMPLETED", "Gate A", "Library", new BigDecimal("40.00"));
    }

    @Test
    void rate_driver_creates_rating_with_correct_fields() {
        RatingView view = ratingService.rateDriver(rideId, riderId, 5, "Great ride!");

        assertNotNull(view.id());
        assertEquals(rideId, view.rideId());
        assertEquals(driverId, view.driverId());
        assertEquals(riderId, view.raterRiderId());
        assertEquals(5, view.driverStars());
        assertEquals("Great ride!", view.comment());
        assertNotNull(view.createdAt());
    }

    @Test
    void rate_driver_rejects_invalid_stars() {
        assertThrows(IllegalArgumentException.class,
                () -> ratingService.rateDriver(rideId, riderId, 6, null));
        assertThrows(IllegalArgumentException.class,
                () -> ratingService.rateDriver(rideId, riderId, 0, null));
    }

    @Test
    void rate_driver_rejects_non_completed_ride() {
        UUID activeRideId = UUID.randomUUID();
        jdbc.update("INSERT INTO rides (id, rider_id, driver_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                        + "VALUES (?,?,?,?,?,?,?,now())",
                activeRideId, riderId, driverId, "IN_PROGRESS", "Gate A", "Library", new BigDecimal("40.00"));

        assertThrows(IllegalArgumentException.class,
                () -> ratingService.rateDriver(activeRideId, riderId, 4, null));
    }

    @Test
    void rate_driver_rejects_wrong_rider() {
        UUID otherRiderId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> ratingService.rateDriver(rideId, otherRiderId, 3, null));
    }

    @Test
    void find_by_ride_and_rider_returns_existing_rating() {
        ratingService.rateDriver(rideId, riderId, 4, "Good");

        Optional<RatingView> found = ratingService.findByRideAndRider(rideId, riderId);

        assertTrue(found.isPresent());
        assertEquals(4, found.get().driverStars());
    }

    @Test
    void find_by_ride_and_rider_returns_empty_when_none() {
        Optional<RatingView> found = ratingService.findByRideAndRider(rideId, riderId);

        assertTrue(found.isEmpty());
    }

    @Test
    void get_driver_stats_returns_average_and_count() {
        UUID rideId2 = UUID.randomUUID();
        String phone2 = "+91" + (7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        UUID riderId2 = UUID.randomUUID();
        jdbc.update("INSERT INTO riders (id, name, phone, created_at) VALUES (?,?,?,now())",
                riderId2, "Second Rider", phone2);
        jdbc.update("INSERT INTO rides (id, rider_id, driver_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                        + "VALUES (?,?,?,?,?,?,?,now())",
                rideId2, riderId2, driverId, "COMPLETED", "Gate B", "Canteen", new BigDecimal("20.00"));

        ratingService.rateDriver(rideId, riderId, 4, null);
        ratingService.rateDriver(rideId2, riderId2, 2, null);

        DriverRatingStats stats = ratingService.getDriverStats(driverId);

        assertEquals(driverId, stats.driverId());
        assertEquals(2L, stats.totalRatings());
        assertEquals(0, stats.averageStars().compareTo(new BigDecimal("3.0")));
    }

    @Test
    void get_driver_stats_with_no_ratings_returns_zero() {
        DriverRatingStats stats = ratingService.getDriverStats(driverId);

        assertEquals(0L, stats.totalRatings());
        assertEquals(0, stats.averageStars().compareTo(BigDecimal.ZERO));
    }
}
