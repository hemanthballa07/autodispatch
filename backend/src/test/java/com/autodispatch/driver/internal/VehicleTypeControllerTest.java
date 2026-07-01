package com.autodispatch.driver.internal;

import com.autodispatch.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
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
 * HTTP-layer coverage for {@link VehicleTypeController} (GET /api/v1/vehicle-types),
 * the rider-facing driver catalog endpoint. Requires a Bearer session token like
 * the rest of the /api/v1 surface. Inserts run inside the test transaction and
 * are rolled back, so the request must join that transaction to observe them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class VehicleTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void requires_a_session_token() throws Exception {
        mockMvc.perform(get("/api/v1/vehicle-types"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns_seeded_auto_type_with_id_and_name() throws Exception {
        JsonNode body = getJson("/api/v1/vehicle-types", sessionToken("VT Auto Rider"));

        assertTrue(body.isArray() && body.size() >= 1, "seed provides at least the Auto type");
        JsonNode auto = null;
        for (JsonNode node : body) {
            if ("Auto".equals(node.path("name").stringValue())) {
                auto = node;
            }
        }
        assertNotNull(auto, "seeded 'Auto' vehicle type must be present");
        assertEquals("Auto", auto.path("name").stringValue());
        assertNotNull(UUID.fromString(auto.path("id").stringValue()), "id must be a UUID");
    }

    @Test
    void excludes_inactive_vehicle_types() throws Exception {
        String hiddenName = "Hidden Type " + suffix();
        insertVehicleType(hiddenName, false);

        JsonNode body = getJson("/api/v1/vehicle-types", sessionToken("VT Inactive Rider"));

        assertFalse(names(body).contains(hiddenName), "inactive vehicle types must not be listed");
    }

    @Test
    void lists_active_types_ordered_by_name_ascending() throws Exception {
        // "AAA ..." sorts before the seeded "Auto".
        String firstName = "AAA Type " + suffix();
        insertVehicleType(firstName, true);

        JsonNode body = getJson("/api/v1/vehicle-types", sessionToken("VT Order Rider"));

        List<String> names = names(body);
        assertTrue(names.contains(firstName));
        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names, "vehicle types must be ordered by name ascending");
    }

    // ---- helpers -------------------------------------------------------------

    private List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(node -> names.add(node.path("name").stringValue()));
        return names;
    }

    private void insertVehicleType(String name, boolean active) {
        jdbc.update("INSERT INTO vehicle_types (id, name, active) VALUES (?,?,?)",
                UUID.randomUUID(), name, active);
    }

    private JsonNode getJson(String uri, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(uri)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String sessionToken(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"phone\":\"+91%s\"}".formatted(name, digits())))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("token").stringValue();
    }

    private static String digits() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
