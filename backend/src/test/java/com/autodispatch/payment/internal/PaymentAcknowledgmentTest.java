package com.autodispatch.payment.internal;

import com.autodispatch.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 HTTP-level gate tests for the payment acknowledgment flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentAcknowledgmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenA;
    private String tokenB;
    private UUID riderAId;
    private UUID rideId;
    private UUID driverId;

    private static final String ADMIN_KEY = "test-admin-key";

    @BeforeEach
    void setup() throws Exception {
        // Create two rider sessions
        SessionResult a = createSession("Ack Rider A");
        SessionResult b = createSession("Ack Rider B");
        tokenA = a.token();
        riderAId = a.riderId();
        tokenB = b.token();

        // Seed a completed ride for rider A directly via JDBC
        rideId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        String waId = "+91" + (9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        jdbc.update("INSERT INTO drivers (id, name, whatsapp_id, vehicle_no, status, verified, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,now(),now())",
                driverId, "HTTP Ledger Driver", waId, "KA-02-8888", "OFFLINE", true);
        jdbc.update("INSERT INTO rides (id, rider_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                + "VALUES (?,?,?,?,?,?,now())",
                rideId, riderAId, "COMPLETED", "Main gate", "Library", new BigDecimal("30.00"));
    }

    // ---- gate 1: initiate + acknowledge happy path ----------------------------

    @Test
    void initiate_and_acknowledge_returns_correct_statuses() throws Exception {
        JsonNode initiated = postJson(
                "/api/v1/rides/" + rideId + "/payment/initiate?amount=30.00", tokenA, 201);
        assertEquals("PENDING", initiated.path("status").textValue());

        JsonNode acked = postJson(
                "/api/v1/rides/" + rideId + "/payment/acknowledge", tokenA, 200);
        assertEquals("COLLECTED", acked.path("status").textValue());
        assertNotNull(acked.path("acknowledgedAt").textValue());
    }

    // ---- gate 2: acknowledge without prior initiation returns 5xx ------------

    @Test
    void acknowledge_without_prior_initiation_fails() throws Exception {
        UUID freshRideId = UUID.randomUUID();
        jdbc.update("INSERT INTO rides (id, rider_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                + "VALUES (?,?,?,?,?,?,now())",
                freshRideId, riderAId, "COMPLETED", "Main gate", "Library", new BigDecimal("30.00"));

        mockMvc.perform(post("/api/v1/rides/" + freshRideId + "/payment/acknowledge")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    // ---- gate 3: cross-rider acknowledgment is rejected ----------------------

    @Test
    void acknowledge_rejected_for_different_rider() throws Exception {
        postJson("/api/v1/rides/" + rideId + "/payment/initiate?amount=30.00", tokenA, 201);

        mockMvc.perform(post("/api/v1/rides/" + rideId + "/payment/acknowledge")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    // ---- gate 4: unauthenticated request returns 401 -------------------------

    @Test
    void unauthenticated_initiate_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/rides/" + rideId + "/payment/initiate?amount=30.00"))
                .andExpect(status().isUnauthorized());
    }

    // ---- gate 5: duplicate initiate for same ride/type returns error ----------

    @Test
    void duplicate_initiate_returns_error() throws Exception {
        postJson("/api/v1/rides/" + rideId + "/payment/initiate?amount=30.00", tokenA, 201);

        mockMvc.perform(post("/api/v1/rides/" + rideId + "/payment/initiate?amount=30.00")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isConflict());
    }

    // ---- gate 6: list transactions for a ride --------------------------------

    @Test
    void list_transactions_returns_initiated_payment() throws Exception {
        postJson("/api/v1/rides/" + rideId + "/payment/initiate?amount=30.00", tokenA, 201);

        MvcResult result = mockMvc.perform(get("/api/v1/rides/" + rideId + "/payment")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertTrue(body.isArray() && body.size() == 1);
        assertEquals("RIDE_FARE", body.get(0).path("type").textValue());
        assertEquals("PENDING", body.get(0).path("status").textValue());
    }

    // ---- gate 7: admin ledger endpoint returns 200 ---------------------------

    @Test
    void admin_ledger_returns_entries_for_driver() throws Exception {
        // seed a ledger entry directly via JDBC to avoid coupling to DriverLedgerService
        UUID entryId = UUID.randomUUID();
        jdbc.update("INSERT INTO driver_ledger (id, driver_id, ride_id, type, amount, created_at) "
                + "VALUES (?,?,?,?,?,now())",
                entryId, driverId, rideId, "EARNING", new BigDecimal("30.00"));

        MvcResult result = mockMvc.perform(get("/api/admin/drivers/" + driverId + "/ledger")
                        .header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertTrue(body.isArray() && body.size() >= 1);
        assertEquals("EARNING", body.get(0).path("type").textValue());
    }

    // ---- helpers -------------------------------------------------------------

    private record SessionResult(UUID riderId, String token) {}

    private SessionResult createSession(String name) throws Exception {
        String phone = "+91" + (7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"phone\":\"%s\"}".formatted(name, phone)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new SessionResult(
                UUID.fromString(body.path("riderId").textValue()),
                body.path("token").textValue());
    }

    private JsonNode postJson(String uri, String token, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(uri)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
