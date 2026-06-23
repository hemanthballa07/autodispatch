package com.autodispatch.rating.internal;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 / Phase 3 HTTP-level gate tests for the rating flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RatingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private UUID riderId;
    private UUID driverId;
    private UUID rideId;

    @BeforeEach
    void setup() throws Exception {
        SessionResult s = createSession("Rating API Rider");
        token = s.token();
        riderId = s.riderId();

        driverId = UUID.randomUUID();
        rideId = UUID.randomUUID();
        String waId = "+91" + (9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L));
        jdbc.update("INSERT INTO drivers (id, name, whatsapp_id, vehicle_no, status, verified, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,now(),now())",
                driverId, "API Rating Driver", waId, "KA-04-5678", "OFFLINE", true);
        jdbc.update("INSERT INTO rides (id, rider_id, driver_id, status, pickup_label, drop_label, fare_amount, requested_at) "
                        + "VALUES (?,?,?,?,?,?,?,now())",
                rideId, riderId, driverId, "COMPLETED", "Main Gate", "Hostel", new BigDecimal("35.00"));
    }

    @Test
    void post_rating_returns_201_with_correct_stars() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/rides/" + rideId + "/rating?stars=5&comment=Excellent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertNotNull(body.path("id").textValue());
        assertEquals(5, body.path("driverStars").intValue());
        assertEquals("Excellent", body.path("comment").textValue());
        assertEquals(driverId.toString(), body.path("driverId").textValue());
    }

    @Test
    void post_rating_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/rides/" + rideId + "/rating?stars=4"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicate_rating_returns_409() throws Exception {
        mockMvc.perform(post("/api/v1/rides/" + rideId + "/rating?stars=5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/rides/" + rideId + "/rating?stars=3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void get_rating_returns_existing_rating() throws Exception {
        mockMvc.perform(post("/api/v1/rides/" + rideId + "/rating?stars=4")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/rides/" + rideId + "/rating")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(4, body.path("driverStars").intValue());
    }

    @Test
    void get_rating_returns_404_when_none() throws Exception {
        mockMvc.perform(get("/api/v1/rides/" + rideId + "/rating")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
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
