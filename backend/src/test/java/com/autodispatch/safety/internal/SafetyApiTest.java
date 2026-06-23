package com.autodispatch.safety.internal;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 / Phase 3 HTTP-level gate tests for the safety flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SafetyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private UUID riderId;
    private UUID rideId;

    private static final String ADMIN_KEY = "test-admin-key";

    @BeforeEach
    void setup() throws Exception {
        SessionResult s = createSession("Safety API Rider");
        token = s.token();
        riderId = s.riderId();

        rideId = UUID.randomUUID();
        jdbc.update("INSERT INTO rides (id, rider_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                        + "VALUES (?,?,?,?,?,?,now())",
                rideId, riderId, "IN_PROGRESS", "Main Gate", "Hostel", new BigDecimal("30.00"));
    }

    @Test
    void post_sos_returns_201_with_sos_type() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/rides/" + rideId + "/safety/sos?details=Help")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertEquals("SOS", body.path("type").textValue());
        assertEquals("Help", body.path("details").textValue());
    }

    @Test
    void post_sos_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/rides/" + rideId + "/safety/sos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_incident_returns_201_with_incident_type() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/rides/" + rideId + "/safety/incident?details=Complaint")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertEquals("INCIDENT_REPORT", body.path("type").textValue());
    }

    @Test
    void admin_can_list_safety_events_for_ride() throws Exception {
        UUID eventId = UUID.randomUUID();
        jdbc.update("INSERT INTO safety_events (id, ride_id, rider_id, type, details, created_at) "
                        + "VALUES (?,?,?,?,?,now())",
                eventId, rideId, riderId, "SOS", "Test SOS");

        MvcResult result = mockMvc.perform(get("/api/admin/rides/" + rideId + "/safety")
                        .header("X-Admin-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertTrue(body.isArray() && body.size() >= 1);
        assertEquals("SOS", body.get(0).path("type").textValue());
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
}
