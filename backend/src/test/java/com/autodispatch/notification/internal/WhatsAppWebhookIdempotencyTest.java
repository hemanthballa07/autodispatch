package com.autodispatch.notification.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.dispatch.api.DispatchApi;
import com.autodispatch.dispatch.internal.Ride;
import com.autodispatch.dispatch.internal.RideRepository;
import com.autodispatch.dispatch.internal.RideStatus;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.driver.internal.Driver;
import com.autodispatch.driver.internal.DriverRepository;
import com.autodispatch.driver.internal.DriverStatus;
import com.autodispatch.rider.internal.Rider;
import com.autodispatch.rider.internal.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.autodispatch.notification.internal.WebhookTestSupport.buttonPayload;
import static com.autodispatch.notification.internal.WebhookTestSupport.sign;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 gate 3: a replayed webhook (same message id) is acknowledged but
 * handleDriverAccept fires exactly once — proven at the dispatch-call level.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WhatsAppWebhookIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private RiderRepository riderRepository;
    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private DriverAvailabilityService driverAvailability;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    @MockitoSpyBean
    private DispatchApi dispatchApi;

    @BeforeEach
    void reset() {
        jdbc.update("UPDATE drivers SET status = 'OFFLINE'");
        redis.delete("drivers:available");
    }

    @Test
    void replayed_message_id_invokes_dispatch_exactly_once() throws Exception {
        Driver driver = driverRepository.save(new Driver(
                "Idem Driver", "+919811112222", "KA-01-IDEM", DriverStatus.OFFLINE, true));
        driverAvailability.goOnline(driver.getId());
        driverAvailability.recordInbound(driver.getId());

        Rider rider = riderRepository.save(new Rider("Idem Rider", "+919811113333"));
        Ride ride = new Ride(rider.getId(), "Main Gate", "Jetty");
        ride.transitionTo(RideStatus.BROADCASTING);
        UUID rideId = rideRepository.save(ride).getId();

        String body = buttonPayload("wamid.idem-001", "919811112222", "ACCEPT:" + rideId);

        for (int delivery = 1; delivery <= 2; delivery++) {
            mockMvc.perform(post("/webhooks/whatsapp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Hub-Signature-256", sign(body))
                            .content(body))
                    .andExpect(status().isOk());
        }

        Mockito.verify(dispatchApi, Mockito.times(1)).handleDriverAccept(anyString(), any(UUID.class));
        assertEquals(RideStatus.ASSIGNED, rideRepository.findById(rideId).orElseThrow().getStatus());
    }
}
