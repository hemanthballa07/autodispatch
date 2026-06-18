package com.autodispatch.admin.internal;

import com.autodispatch.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
 * Phase 6 gates 1–6: admin API end-to-end against Testcontainers Postgres + Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminApiIntegrationTest {

    static final String ADMIN_KEY = "test-admin-key";

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
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void reset() {
        jdbc.update("UPDATE drivers SET status = 'OFFLINE', suspended = FALSE");
        redis.delete("drivers:available");
    }

    // ---- gate 1: auth guard -------------------------------------------------

    @Test
    void no_key_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/v1/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_key_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/v1/stats")
                        .header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correct_key_returns_200_on_stats() throws Exception {
        mockMvc.perform(get("/api/admin/v1/stats")
                        .header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().isOk());
    }

    // ---- gate 2: driver registration ----------------------------------------

    @Test
    void register_new_driver_returns_201_offline_unverified_unsuspended() throws Exception {
        String wa = "+9190" + digits(8);
        JsonNode body = adminPost("/api/admin/v1/drivers/",
                "{\"name\":\"Test Driver\",\"whatsappId\":\"%s\",\"vehicleNo\":\"KA-01-%s\"}"
                        .formatted(wa, digits(4)), 201);

        UUID driverId = UUID.fromString(body.path("id").stringValue());
        assertEquals("Test Driver", body.path("name").stringValue());
        assertEquals("OFFLINE", body.path("state").stringValue());
        assertFalse(body.path("verified").asBoolean(), "newly registered driver must be unverified");
        assertFalse(body.path("suspended").asBoolean(), "newly registered driver must be unsuspended");

        // DB row exists
        assertTrue(driverRepository.findById(driverId).isPresent());
        // Redis available set untouched
        assertFalse(Boolean.TRUE.equals(
                redis.opsForSet().isMember("drivers:available", driverId.toString())));
    }

    @Test
    void duplicate_whatsapp_id_returns_409() throws Exception {
        String wa = "+9191" + digits(8);
        adminPost("/api/admin/v1/drivers/",
                "{\"name\":\"D1\",\"whatsappId\":\"%s\",\"vehicleNo\":\"KA-01-0001\"}".formatted(wa), 201);
        adminPost("/api/admin/v1/drivers/",
                "{\"name\":\"D2\",\"whatsappId\":\"%s\",\"vehicleNo\":\"KA-01-0002\"}".formatted(wa), 409);
    }

    // ---- gate 3: verify enables dispatch ------------------------------------

    @Test
    void unverified_driver_absent_from_available_list_after_online() {
        Driver d = savedDriver(false);
        driverAvailability.goOnline(d.getId());
        driverAvailability.recordInbound(d.getId());
        assertTrue(driverAvailability.listAvailableVerified().stream()
                .noneMatch(s -> s.id().equals(d.getId())), "unverified driver must not be in available list");
    }

    @Test
    void verify_enables_driver_in_available_list() throws Exception {
        Driver d = savedDriver(false);
        driverAvailability.goOnline(d.getId());
        driverAvailability.recordInbound(d.getId());

        adminPost("/api/admin/v1/drivers/" + d.getId() + "/verify", null, 200);

        assertTrue(driverAvailability.listAvailableVerified().stream()
                .anyMatch(s -> s.id().equals(d.getId())), "verified + online driver must be in available list");
    }

    @Test
    void verify_is_idempotent() throws Exception {
        Driver d = savedDriver(true);
        adminPost("/api/admin/v1/drivers/" + d.getId() + "/verify", null, 200);
        adminPost("/api/admin/v1/drivers/" + d.getId() + "/verify", null, 200);
    }

    // ---- gate 4: suspend / unsuspend ----------------------------------------

    @Test
    void suspend_available_driver_sets_offline_suspended_removes_from_redis() throws Exception {
        Driver d = savedDriver(true);
        driverAvailability.goOnline(d.getId());

        JsonNode body = adminPost("/api/admin/v1/drivers/" + d.getId() + "/suspend", null, 200);

        assertTrue(body.path("suspended").asBoolean());
        assertEquals("OFFLINE", body.path("state").stringValue());

        Driver inDb = driverRepository.findById(d.getId()).orElseThrow();
        assertTrue(inDb.isSuspended());
        assertEquals(DriverStatus.OFFLINE, inDb.getStatus());
        assertFalse(Boolean.TRUE.equals(
                redis.opsForSet().isMember("drivers:available", d.getId().toString())));
    }

    @Test
    void suspend_on_ride_driver_returns_422() throws Exception {
        Driver d = savedDriver(true);
        jdbc.update("UPDATE drivers SET status = 'ON_RIDE' WHERE id = ?", d.getId());

        adminPost("/api/admin/v1/drivers/" + d.getId() + "/suspend", null, 422);
    }

    @Test
    void unsuspend_clears_flag_driver_stays_offline() throws Exception {
        Driver d = savedDriver(true);
        adminPost("/api/admin/v1/drivers/" + d.getId() + "/suspend", null, 200);

        JsonNode body = adminPost("/api/admin/v1/drivers/" + d.getId() + "/unsuspend", null, 200);

        assertFalse(body.path("suspended").asBoolean());
        assertEquals("OFFLINE", body.path("state").stringValue());
    }

    // ---- gate 5: ride listing -----------------------------------------------

    @Test
    void ride_listing_and_filtering() throws Exception {
        UUID riderId = seedRider();
        Ride ride = rideRepository.save(new Ride(riderId, "Main gate", "Library", BigDecimal.valueOf(30)));
        UUID rideId = ride.getId();

        // list all
        JsonNode list = adminGet("/api/admin/v1/rides/", 200);
        assertTrue(list.isArray() && list.size() >= 1);
        assertTrue(anyMatchId(list, rideId));

        // filter by status
        JsonNode filtered = adminGet("/api/admin/v1/rides/?status=REQUESTED", 200);
        assertTrue(anyMatchId(filtered, rideId));

        // filter by today's date
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        JsonNode byDate = adminGet("/api/admin/v1/rides/?date=" + today, 200);
        assertTrue(anyMatchId(byDate, rideId));

        // single ride
        JsonNode detail = adminGet("/api/admin/v1/rides/" + rideId, 200);
        assertEquals(rideId.toString(), detail.path("id").stringValue());
        assertEquals("REQUESTED", detail.path("status").stringValue());
        assertNotNull(detail.path("riderId").stringValue());

        // unknown id → 404
        adminGet("/api/admin/v1/rides/" + UUID.randomUUID(), 404);
    }

    @Test
    void invalid_status_filter_returns_400() throws Exception {
        adminGet("/api/admin/v1/rides/?status=BOGUS", 400);
    }

    // ---- gate 6: stats -------------------------------------------------------

    @Test
    void stats_counts_active_completed_and_available() throws Exception {
        // Reset rides to terminal state so counts are predictable
        jdbc.update("UPDATE rides SET status = 'EXPIRED' WHERE status IN ('REQUESTED','BROADCASTING','ASSIGNED','ARRIVED','IN_PROGRESS')");

        // 2 BROADCASTING rides
        UUID r1Id = seedRider();
        Ride ride1 = rideRepository.saveAndFlush(new Ride(r1Id, "A", "B", BigDecimal.valueOf(10)));
        jdbc.update("UPDATE rides SET status = 'BROADCASTING', current_round_expires_at = now() + interval '1 hour', broadcast_round = 1 WHERE id = ?", ride1.getId());

        UUID r2Id = seedRider();
        Ride ride2 = rideRepository.saveAndFlush(new Ride(r2Id, "C", "D", BigDecimal.valueOf(10)));
        jdbc.update("UPDATE rides SET status = 'BROADCASTING', current_round_expires_at = now() + interval '1 hour', broadcast_round = 1 WHERE id = ?", ride2.getId());

        // 1 COMPLETED today
        UUID r3Id = seedRider();
        Ride ride3 = rideRepository.saveAndFlush(new Ride(r3Id, "E", "F", BigDecimal.valueOf(10)));
        jdbc.update("UPDATE rides SET status = 'COMPLETED', completed_at = now() WHERE id = ?", ride3.getId());

        // 1 AVAILABLE verified driver (unverified drivers excluded by listAvailableVerified)
        Driver d = savedDriver(true);
        driverAvailability.goOnline(d.getId());
        driverAvailability.recordInbound(d.getId());

        JsonNode stats = adminGet("/api/admin/v1/stats", 200);
        assertEquals(2, stats.path("activeRides").asLong());
        assertEquals(1, stats.path("completedToday").asLong());
        assertEquals(1, stats.path("availableDrivers").asLong());
    }

    // ---- helpers ------------------------------------------------------------

    private JsonNode adminPost(String uri, String body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder req = post(uri).header("X-Admin-Key", ADMIN_KEY);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult result = mockMvc.perform(req)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode adminGet(String uri, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(uri).header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private Driver savedDriver(boolean verified) {
        return driverRepository.save(new Driver(
                "Driver " + digits(5), "+9192" + digits(8), "KA-02-" + digits(4),
                DriverStatus.OFFLINE, verified));
    }

    private UUID seedRider() {
        return UUID.fromString(jdbc.queryForObject(
                "INSERT INTO riders (id, name, phone, created_at) VALUES (gen_random_uuid(), ?, ?, now()) RETURNING id::text",
                String.class,
                "Rider " + digits(5), "+9193" + digits(8)));
    }

    private static boolean anyMatchId(JsonNode array, UUID id) {
        for (JsonNode node : array) {
            if (id.toString().equals(node.path("id").stringValue())) {
                return true;
            }
        }
        return false;
    }

    private static String digits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }
}
