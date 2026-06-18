package com.autodispatch.notification.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.dispatch.api.DispatchApi;
import com.autodispatch.dispatch.internal.Ride;
import com.autodispatch.dispatch.internal.RideRepository;
import com.autodispatch.dispatch.internal.RideStatus;
import com.autodispatch.driver.internal.Driver;
import com.autodispatch.driver.internal.DriverRepository;
import com.autodispatch.driver.internal.DriverStatus;
import com.autodispatch.notification.api.MessageCatalog;
import com.autodispatch.rider.internal.Rider;
import com.autodispatch.rider.internal.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.autodispatch.notification.internal.WebhookTestSupport.buttonPayload;
import static com.autodispatch.notification.internal.WebhookTestSupport.sign;
import static com.autodispatch.notification.internal.WebhookTestSupport.textPayload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 gate 5: full driver journey through the webhook in STUB mode —
 * ON → offer → button accept → ARRIVED → START → DONE — asserting the
 * outbound message sequence and the final domain state. No external calls.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WhatsAppDriverE2ETest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private RiderRepository riderRepository;
    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private DispatchApi dispatchApi;
    @Autowired
    private RecordingWhatsAppGateway gateway;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    private int messageSeq = 0;

    @BeforeEach
    void reset() {
        jdbc.update("UPDATE drivers SET status = 'OFFLINE'");
        redis.delete("drivers:available");
        gateway.clear();
    }

    @Test
    void full_driver_journey_on_offer_accept_arrived_start_done() throws Exception {
        Driver driver = driverRepository.save(new Driver(
                "E2E Driver", "+919822334455", "KA-01-E2E", DriverStatus.OFFLINE, true));
        String driverWa = "919822334455"; // Meta "from" format: no plus
        Rider rider = riderRepository.save(new Rider("E2E Rider", "+919822334466"));

        // 1) ON → driver online + ack
        postText(driverWa, "ON");
        assertEquals(DriverStatus.AVAILABLE, driverStatus(driver.getId()));
        assertTrue(textsTo("+919822334455").contains(MessageCatalog.goOnlineAck()));

        // 2) Ride requested → offer broadcast to the driver
        UUID rideId = rideRepository.save(new Ride(rider.getId(), "Main Gate", "Jetty")).getId();
        dispatchApi.startDispatch(rideId);
        assertEquals(RideStatus.BROADCASTING, rideStatus(rideId));
        assertTrue(textsTo("+919822334455").stream().anyMatch(t -> t.startsWith("New ride: Main Gate → Jetty")),
                "offer message sent to driver");

        // 3) Button accept → ASSIGNED, driver ON_RIDE, rider notified
        postButton(driverWa, "ACCEPT:" + rideId);
        assertEquals(RideStatus.ASSIGNED, rideStatus(rideId));
        assertEquals(DriverStatus.ON_RIDE, driverStatus(driver.getId()));
        assertTrue(textsTo("+919822334466").contains(
                MessageCatalog.driverAssigned("E2E Driver", "KA-01-E2E", "Main Gate")));

        // 4) ARRIVED → 5) START → 6) DONE
        postText(driverWa, "ARRIVED");
        assertEquals(RideStatus.ARRIVED, rideStatus(rideId));
        postText(driverWa, "START");
        assertEquals(RideStatus.IN_PROGRESS, rideStatus(rideId));
        postText(driverWa, "DONE");
        assertEquals(RideStatus.COMPLETED, rideStatus(rideId));
        assertEquals(DriverStatus.AVAILABLE, driverStatus(driver.getId()));

        // Outbound sequence to the driver, in order.
        List<String> driverTexts = textsTo("+919822334455");
        assertOrderedSubsequence(driverTexts,
                MessageCatalog.goOnlineAck(),
                MessageCatalog.arrivedAck(),
                MessageCatalog.startedAck(),
                MessageCatalog.completedAck());
    }

    @Test
    void unknown_sender_gets_polite_canned_reply_and_no_command_runs() throws Exception {
        postText("917700000001", "ON");
        assertTrue(textsTo("+917700000001").contains(MessageCatalog.unknownSender()));
    }

    private void postText(String fromWa, String text) throws Exception {
        String body = textPayload("wamid.e2e-" + (++messageSeq) + "-" + UUID.randomUUID(), fromWa, text);
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    private void postButton(String fromWa, String buttonId) throws Exception {
        String body = buttonPayload("wamid.e2e-" + (++messageSeq) + "-" + UUID.randomUUID(), fromWa, buttonId);
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    private List<String> textsTo(String recipient) {
        return gateway.recorded().stream()
                .filter(m -> m.recipient().equals(recipient))
                .map(RecordingWhatsAppGateway.RecordedMessage::text)
                .toList();
    }

    private DriverStatus driverStatus(UUID driverId) {
        return driverRepository.findById(driverId).orElseThrow().getStatus();
    }

    private RideStatus rideStatus(UUID rideId) {
        return rideRepository.findById(rideId).orElseThrow().getStatus();
    }

    private static void assertOrderedSubsequence(List<String> actual, String... expectedInOrder) {
        int idx = 0;
        for (String expected : expectedInOrder) {
            int found = -1;
            for (int i = idx; i < actual.size(); i++) {
                if (actual.get(i).equals(expected)) {
                    found = i;
                    break;
                }
            }
            assertTrue(found >= 0, "expected message in order: " + expected);
            idx = found + 1;
        }
    }
}
