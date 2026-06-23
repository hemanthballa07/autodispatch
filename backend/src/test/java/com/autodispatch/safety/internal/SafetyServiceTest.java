package com.autodispatch.safety.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.safety.api.SafetyEventView;
import com.autodispatch.safety.api.SafetyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 10 / Phase 3 gate tests — safety service layer.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SafetyServiceTest {

    @Autowired
    private SafetyService safetyService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID riderId;
    private UUID rideId;

    @BeforeEach
    void seed() {
        riderId = UUID.randomUUID();
        rideId = UUID.randomUUID();
        String phone = "+91" + (7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        jdbc.update("INSERT INTO riders (id, name, phone, created_at) VALUES (?,?,?,now())",
                riderId, "Safety Rider", phone);
        jdbc.update("INSERT INTO rides (id, rider_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                        + "VALUES (?,?,?,?,?,?,now())",
                rideId, riderId, "IN_PROGRESS", "Main Gate", "Hostel", new BigDecimal("30.00"));
    }

    @Test
    void trigger_sos_creates_sos_event() {
        SafetyEventView view = safetyService.triggerSos(rideId, riderId, "Help needed");

        assertNotNull(view.id());
        assertEquals(rideId, view.rideId());
        assertEquals(riderId, view.riderId());
        assertEquals("SOS", view.type());
        assertEquals("Help needed", view.details());
        assertNotNull(view.createdAt());
    }

    @Test
    void report_incident_creates_incident_event() {
        SafetyEventView view = safetyService.reportIncident(rideId, riderId, "Driver was rude");

        assertEquals("INCIDENT_REPORT", view.type());
        assertEquals("Driver was rude", view.details());
    }

    @Test
    void find_by_ride_returns_all_events() {
        safetyService.triggerSos(rideId, riderId, "SOS 1");
        safetyService.reportIncident(rideId, riderId, "Incident 1");

        List<SafetyEventView> events = safetyService.findByRide(rideId);

        assertEquals(2, events.size());
    }

    @Test
    void trigger_sos_rejects_wrong_rider() {
        UUID otherRiderId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> safetyService.triggerSos(rideId, otherRiderId, "fake sos"));
    }
}
