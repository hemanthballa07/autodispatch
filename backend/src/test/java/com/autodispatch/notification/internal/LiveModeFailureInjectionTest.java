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
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 gate 6 (integration half): LIVE mode against WireMock returning 500
 * on every send — domain transitions still commit; outbound failure is logged
 * and dropped, never propagated.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LiveModeFailureInjectionTest {

    private static final WireMockServer wireMock =
            new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void liveModeProps(DynamicPropertyRegistry registry) {
        wireMock.start();
        wireMock.stubFor(post(urlPathMatching("/.*/messages"))
                .willReturn(aResponse().withStatus(500)));
        registry.add("whatsapp.mode", () -> "LIVE");
        registry.add("whatsapp.api-base-url", wireMock::baseUrl);
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private RiderRepository riderRepository;
    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private DriverAvailabilityService driverAvailability;
    @Autowired
    private DispatchApi dispatchApi;

    @Test
    void domain_transitions_commit_even_when_every_send_fails() {
        Driver driver = driverRepository.save(new Driver(
                "Live Driver", "+919833445566", "KA-01-LIVE", DriverStatus.OFFLINE, true));
        driverAvailability.goOnline(driver.getId());
        driverAvailability.recordInbound(driver.getId());
        Rider rider = riderRepository.save(new Rider("Live Rider", "+919833445577"));
        UUID rideId = rideRepository.save(new Ride(rider.getId(), "Main Gate", "Jetty")).getId();

        dispatchApi.startDispatch(rideId);                              // offer send → 500 ×2
        dispatchApi.handleDriverAccept("+919833445566", rideId);        // rider notify → 500 ×2

        assertEquals(RideStatus.ASSIGNED,
                rideRepository.findById(rideId).orElseThrow().getStatus(),
                "transition committed despite send failures");
        assertEquals(DriverStatus.ON_RIDE,
                driverRepository.findById(driver.getId()).orElseThrow().getStatus());
        assertTrue(wireMock.getAllServeEvents().size() >= 2,
                "failed sends were attempted (with retry) against the API");
    }
}
