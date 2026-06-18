package com.autodispatch.rider.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.dispatch.api.DispatchApi;
import com.autodispatch.dispatch.internal.Ride;
import com.autodispatch.dispatch.internal.RideRepository;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.driver.internal.Driver;
import com.autodispatch.driver.internal.DriverRepository;
import com.autodispatch.driver.internal.DriverStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 gates 1–6: the rider API end-to-end against Testcontainers
 * Postgres + Redis, WhatsApp in STUB mode throughout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RiderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private DriverAvailabilityService driverAvailability;
    @Autowired
    private DispatchApi dispatchApi;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    private Map<String, UUID> locationsByName;

    @BeforeEach
    void reset() throws Exception {
        jdbc.update("UPDATE drivers SET status = 'OFFLINE'");
        redis.delete("drivers:available");
        Set<String> rateKeys = redis.keys("rate:ride-create:*");
        if (rateKeys != null && !rateKeys.isEmpty()) {
            redis.delete(rateKeys);
        }
        if (locationsByName == null) {
            locationsByName = fetchLocations();
        }
    }

    // ---- gate 1: full happy path with rider-visible views -------------------

    @Test
    void happy_path_request_to_completed_with_views_at_each_state() throws Exception {
        String token = sessionToken("Happy Rider");
        Driver driver = onlineDriver();

        // fare estimate before booking (CORE → ACADEMIC = 30.00 seed)
        JsonNode estimate = getJson("/api/v1/fares/estimate?pickupId=%s&dropId=%s"
                .formatted(loc("Main gate"), loc("Library")), token, 200);
        assertEquals(0, estimate.path("amount").decimalValue()
                .compareTo(new java.math.BigDecimal("30.00")));

        JsonNode created = postRide(token, loc("Main gate"), loc("Library"), 201);
        UUID rideId = UUID.fromString(created.path("id").stringValue());
        assertEquals("BROADCASTING", created.path("status").stringValue());
        assertTrue(created.path("cancellable").asBoolean());
        assertTrue(created.path("driver").isNull(), "no driver card before assignment");

        dispatchApi.handleDriverAccept(driver.getWhatsappId(), rideId);
        JsonNode assigned = getJson("/api/v1/rides/" + rideId, token, 200);
        assertEquals("ASSIGNED", assigned.path("status").stringValue());
        assertEquals(driver.getName(), assigned.path("driver").path("name").stringValue());
        assertEquals(driver.getVehicleNo(), assigned.path("driver").path("vehicleNo").stringValue());
        String masked = assigned.path("driver").path("maskedPhone").stringValue();
        assertTrue(masked.contains("•"), "phone must be masked");
        assertTrue(masked.endsWith(driver.getWhatsappId().substring(driver.getWhatsappId().length() - 4)));
        assertFalse(assigned.toString().contains(driver.getWhatsappId()),
                "raw wa_id must never appear in a rider view");

        dispatchApi.markArrived(rideId);
        assertEquals("ARRIVED", rideStatusVia(token, rideId));
        assertFalse(getJson("/api/v1/rides/" + rideId, token, 200).path("cancellable").asBoolean());

        dispatchApi.markStarted(rideId);
        assertEquals("IN_PROGRESS", rideStatusVia(token, rideId));

        dispatchApi.markCompleted(rideId);
        JsonNode completed = getJson("/api/v1/rides/" + rideId, token, 200);
        assertEquals("COMPLETED", completed.path("status").stringValue());
        assertNotNull(completed.path("completedAt").stringValue());

        JsonNode history = getJson("/api/v1/rides?mine=true", token, 200);
        assertTrue(history.isArray() && history.size() >= 1);
        assertEquals(rideId.toString(), history.get(0).path("id").stringValue(), "newest first");
    }

    // ---- gate 2: one active ride per rider -----------------------------------

    @Test
    void second_ride_while_one_is_active_is_409() throws Exception {
        String token = sessionToken("Eager Rider");
        onlineDriver(); // keeps the first ride BROADCASTING (active)

        postRide(token, loc("Hostel gate"), loc("Library"), 201);
        postRide(token, loc("Hostel gate"), loc("Library"), 409);
    }

    // ---- gate 3: authz — no existence leak ------------------------------------

    @Test
    void rider_cannot_see_or_cancel_another_riders_ride() throws Exception {
        String tokenA = sessionToken("Rider A");
        String tokenB = sessionToken("Rider B");
        onlineDriver();

        UUID rideId = UUID.fromString(
                postRide(tokenA, loc("Jetty"), loc("Main gate"), 201).path("id").stringValue());

        mockMvc.perform(get("/api/v1/rides/" + rideId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/rides/" + rideId + "/cancel")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        // owner still sees it
        getJson("/api/v1/rides/" + rideId, tokenA, 200);
    }

    // ---- gate 4: fare quote immutability ---------------------------------------

    @Test
    void fare_rule_change_after_booking_does_not_alter_the_quote() throws Exception {
        String token = sessionToken("Quoted Rider");
        onlineDriver();

        JsonNode created = postRide(token, loc("Hostel gate"), loc("Jetty"), 201); // HOSTELS→TRANSPORT 50.00
        UUID rideId = UUID.fromString(created.path("id").stringValue());
        assertEquals(0, created.path("fare").decimalValue()
                .compareTo(new java.math.BigDecimal("50.00")));

        jdbc.update("UPDATE fare_rules SET amount = 99.00 "
                + "WHERE pickup_zone = 'HOSTELS' AND drop_zone = 'TRANSPORT'");
        try {
            JsonNode after = getJson("/api/v1/rides/" + rideId, token, 200);
            assertEquals(0, after.path("fare").decimalValue()
                            .compareTo(new java.math.BigDecimal("50.00")),
                    "quote captured at request time is immutable");
        } finally {
            jdbc.update("UPDATE fare_rules SET amount = 50.00 "
                    + "WHERE pickup_zone = 'HOSTELS' AND drop_zone = 'TRANSPORT'");
        }
    }

    // ---- gate 5: rate limit ------------------------------------------------------

    @Test
    void fourth_ride_creation_in_window_is_429() throws Exception {
        String token = sessionToken("Busy Rider");
        onlineDriver();

        for (int i = 0; i < 3; i++) {
            UUID rideId = UUID.fromString(
                    postRide(token, loc("Bus stand"), loc("Admin block"), 201).path("id").stringValue());
            mockMvc.perform(post("/api/v1/rides/" + rideId + "/cancel")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        postRide(token, loc("Bus stand"), loc("Admin block"), 429);
    }

    // ---- gate 6: cancel matrix ----------------------------------------------------

    @Test
    void cancel_allowed_in_requested_broadcasting_assigned_and_409_after() throws Exception {
        // REQUESTED (created directly — the API broadcasts immediately)
        var requested = riderWithToken("Matrix R");
        UUID requestedRide = rideRepository.save(
                new Ride(requested.riderId(), "Main gate", "Library")).getId();
        cancelExpecting(requested.token(), requestedRide, 200);

        // BROADCASTING
        var broadcasting = riderWithToken("Matrix B");
        onlineDriver();
        UUID broadcastingRide = UUID.fromString(
                postRide(broadcasting.token(), loc("Main gate"), loc("Library"), 201)
                        .path("id").stringValue());
        cancelExpecting(broadcasting.token(), broadcastingRide, 200);

        // ASSIGNED — and the driver must be freed
        var assigned = riderWithToken("Matrix A");
        Driver assignedDriver = onlineDriver();
        UUID assignedRide = UUID.fromString(
                postRide(assigned.token(), loc("Main gate"), loc("Library"), 201)
                        .path("id").stringValue());
        dispatchApi.handleDriverAccept(assignedDriver.getWhatsappId(), assignedRide);
        cancelExpecting(assigned.token(), assignedRide, 200);
        assertEquals(DriverStatus.AVAILABLE,
                driverRepository.findById(assignedDriver.getId()).orElseThrow().getStatus(),
                "driver freed when rider cancels an assigned ride");

        // ARRIVED → 409
        var arrived = riderWithToken("Matrix AR");
        Driver arrivedDriver = onlineDriver();
        UUID arrivedRide = UUID.fromString(
                postRide(arrived.token(), loc("Main gate"), loc("Library"), 201)
                        .path("id").stringValue());
        dispatchApi.handleDriverAccept(arrivedDriver.getWhatsappId(), arrivedRide);
        dispatchApi.markArrived(arrivedRide);
        cancelExpecting(arrived.token(), arrivedRide, 409);

        // IN_PROGRESS → 409
        dispatchApi.markStarted(arrivedRide);
        cancelExpecting(arrived.token(), arrivedRide, 409);

        // COMPLETED (terminal) → 409
        dispatchApi.markCompleted(arrivedRide);
        cancelExpecting(arrived.token(), arrivedRide, 409);
    }

    // ---- helpers ---------------------------------------------------------------------

    private record RiderSession(UUID riderId, String token) {
    }

    private RiderSession riderWithToken(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"phone\":\"+91%s\"}".formatted(name, digits(10))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new RiderSession(UUID.fromString(body.path("riderId").stringValue()),
                body.path("token").stringValue());
    }

    private String sessionToken(String name) throws Exception {
        return riderWithToken(name).token();
    }

    private Driver onlineDriver() {
        Driver driver = driverRepository.save(new Driver(
                "API Driver " + digits(5), "+91" + digits(10), "KA-01-" + digits(4),
                DriverStatus.OFFLINE, true));
        driverAvailability.goOnline(driver.getId());
        driverAvailability.recordInbound(driver.getId());
        return driver;
    }

    private JsonNode postRide(String token, UUID pickupId, UUID dropId, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/rides")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupId\":\"%s\",\"dropId\":\"%s\"}".formatted(pickupId, dropId)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private void cancelExpecting(String token, UUID rideId, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/rides/" + rideId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode getJson(String uri, String token, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(uri)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String rideStatusVia(String token, UUID rideId) throws Exception {
        return getJson("/api/v1/rides/" + rideId, token, 200).path("status").stringValue();
    }

    private UUID loc(String name) {
        UUID id = locationsByName.get(name);
        assertNotNull(id, "seeded location missing: " + name);
        return id;
    }

    private Map<String, UUID> fetchLocations() throws Exception {
        String token = sessionToken("Bootstrap Rider");
        JsonNode body = getJson("/api/v1/locations", token, 200);
        Map<String, UUID> byName = new HashMap<>();
        body.forEach(node -> byName.put(node.path("name").stringValue(),
                UUID.fromString(node.path("id").stringValue())));
        return byName;
    }

    private static String digits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }
}
