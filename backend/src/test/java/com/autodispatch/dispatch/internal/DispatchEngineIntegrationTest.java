package com.autodispatch.dispatch.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.driver.internal.Driver;
import com.autodispatch.driver.internal.DriverRepository;
import com.autodispatch.driver.internal.DriverStatus;
import com.autodispatch.notification.api.MessageCatalog;
import com.autodispatch.notification.internal.RecordingWhatsAppGateway;
import com.autodispatch.rider.internal.Rider;
import com.autodispatch.rider.internal.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 gates 1–6 against real Postgres + Redis (Testcontainers). The
 * scheduler is disabled in tests; sweep steps are invoked directly.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DispatchEngineIntegrationTest {

    private static final String AVAILABLE_KEY = "drivers:available";

    @Autowired
    private DispatchService dispatchService;
    @Autowired
    private RideRepository rideRepository;
    @Autowired
    private RideOfferRepository offerRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private RiderRepository riderRepository;
    @Autowired
    private DriverAvailabilityService driverAvailability;
    @Autowired
    private RecordingWhatsAppGateway gateway;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void reset() {
        jdbc.update("UPDATE drivers SET status = 'OFFLINE'");
        redis.delete(AVAILABLE_KEY);
        gateway.clear();
    }

    // ---- gate 1: 5 concurrent accepts, exactly one winner -----------------

    @Test
    void race_five_concurrent_accepts_exactly_one_wins() throws Exception {
        Rider rider = newRider();
        List<Driver> drivers = onlineDrivers(5);
        UUID rideId = newRequestedRide(rider);

        dispatchService.startDispatch(rideId);
        assertEquals(5, offerRepository.findByRideId(rideId).size());

        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        var futures = drivers.stream().map(d -> pool.submit(() -> {
            start.await();
            dispatchService.handleDriverAccept(d.getWhatsappId(), rideId);
            return null;
        })).toList();
        start.countDown();
        for (var f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        List<RideOffer> offers = offerRepository.findByRideId(rideId);
        assertEquals(1, offers.stream().filter(o -> o.getResponse() == OfferResponse.ACCEPTED).count(),
                "exactly one ACCEPTED offer");
        assertEquals(4, offers.stream().filter(o -> o.getResponse() == OfferResponse.TOO_LATE).count(),
                "four TOO_LATE offers");

        Ride ride = rideRepository.findById(rideId).orElseThrow();
        assertEquals(RideStatus.ASSIGNED, ride.getStatus());
        assertNotNull(ride.getDriverId());

        long onRide = drivers.stream()
                .map(d -> driverRepository.findById(d.getId()).orElseThrow().getStatus())
                .filter(s -> s == DriverStatus.ON_RIDE)
                .count();
        assertEquals(1, onRide, "exactly one ON_RIDE driver");

        Set<String> available = redis.opsForSet().members(AVAILABLE_KEY);
        Set<String> expectedLosers = drivers.stream()
                .map(d -> d.getId().toString())
                .filter(id -> !id.equals(ride.getDriverId().toString()))
                .collect(Collectors.toSet());
        assertEquals(expectedLosers, available, "Redis available set holds exactly the 4 losers");
    }

    // ---- gate 2: accept races the sweeper at round expiry ------------------

    @Test
    void accept_vs_sweeper_race_ride_ends_assigned_and_consistent() throws Exception {
        Rider rider = newRider();
        List<Driver> drivers = onlineDrivers(2);
        Driver accepting = drivers.get(0);
        UUID rideId = newRequestedRide(rider);
        dispatchService.startDispatch(rideId);
        forceRoundExpired(rideId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var acceptF = pool.submit(() -> {
            start.await();
            dispatchService.handleDriverAccept(accepting.getWhatsappId(), rideId);
            return null;
        });
        var sweepF = pool.submit(() -> {
            start.await();
            dispatchService.sweepOne(rideId);
            return null;
        });
        start.countDown();
        acceptF.get(20, TimeUnit.SECONDS);
        sweepF.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        Ride ride = rideRepository.findById(rideId).orElseThrow();
        assertEquals(RideStatus.ASSIGNED, ride.getStatus(), "accept must stick regardless of sweeper");
        assertEquals(accepting.getId(), ride.getDriverId());
        assertTrue(ride.getBroadcastRound() <= 2, "never double-rounded");

        List<RideOffer> offers = offerRepository.findByRideId(rideId);
        assertEquals(2, offers.size(), "unique(ride_id, driver_id) — no duplicate offer rows");
        assertEquals(1, offers.stream().filter(o -> o.getResponse() == OfferResponse.ACCEPTED).count());
    }

    // ---- gate 3: full timeout path -----------------------------------------

    @Test
    void three_rounds_without_accepts_expire_ride_and_notify_rider() {
        Rider rider = newRider();
        onlineDrivers(2);
        UUID rideId = newRequestedRide(rider);
        dispatchService.startDispatch(rideId);

        for (int sweep = 1; sweep <= 3; sweep++) {
            forceRoundExpired(rideId);
            dispatchService.sweepOne(rideId);
        }

        Ride ride = rideRepository.findById(rideId).orElseThrow();
        assertEquals(RideStatus.EXPIRED, ride.getStatus());
        assertEquals(3, ride.getBroadcastRound());

        List<RideOffer> offers = offerRepository.findByRideId(rideId);
        assertTrue(offers.stream().allMatch(o -> o.getResponse() == OfferResponse.IGNORED),
                "all unanswered offers marked IGNORED");

        assertTrue(gateway.recorded().stream().anyMatch(m ->
                        m.recipient().equals(rider.getPhone())
                                && m.text().equals(MessageCatalog.rideExpired())),
                "rider notified of expiry");
    }

    // ---- gate 4: driver cancel — one rebroadcast, then expired --------------

    @Test
    void driver_cancel_rebroadcasts_once_then_expires() {
        Rider rider = newRider();
        List<Driver> drivers = onlineDrivers(2);
        Driver first = drivers.get(0);
        Driver second = drivers.get(1);
        UUID rideId = newRequestedRide(rider);
        dispatchService.startDispatch(rideId);

        dispatchService.handleDriverAccept(first.getWhatsappId(), rideId);
        assertEquals(RideStatus.ASSIGNED, status(rideId));

        dispatchService.handleDriverCancel(rideId);
        Ride afterCancel = rideRepository.findById(rideId).orElseThrow();
        assertEquals(RideStatus.BROADCASTING, afterCancel.getStatus());
        assertEquals(1, afterCancel.getRebroadcastCount());
        assertEquals(2, afterCancel.getBroadcastRound(), "fresh round after rebroadcast");
        assertNull(afterCancel.getDriverId());
        assertEquals(DriverStatus.AVAILABLE,
                driverRepository.findById(first.getId()).orElseThrow().getStatus());
        assertTrue(redis.opsForSet().isMember(AVAILABLE_KEY, first.getId().toString()),
                "cancelling driver back in the Redis available set");

        RideOffer reissued = offerRepository.findByRideIdAndDriverId(rideId, second.getId()).orElseThrow();
        assertEquals(2, reissued.getRound());
        assertNull(reissued.getResponse());

        dispatchService.handleDriverAccept(second.getWhatsappId(), rideId);
        assertEquals(RideStatus.ASSIGNED, status(rideId));

        dispatchService.handleDriverCancel(rideId);
        Ride afterSecondCancel = rideRepository.findById(rideId).orElseThrow();
        assertEquals(RideStatus.EXPIRED, afterSecondCancel.getStatus(), "guard exhausted → EXPIRED");
        assertNull(afterSecondCancel.getDriverId());
        assertEquals(DriverStatus.AVAILABLE,
                driverRepository.findById(second.getId()).orElseThrow().getStatus());
        assertTrue(gateway.recorded().stream().anyMatch(m ->
                m.recipient().equals(rider.getPhone())
                        && m.text().equals(MessageCatalog.rideExpired())));
    }

    // ---- gate 5: compensation path ------------------------------------------

    @Test
    void compensation_reverts_claim_when_driver_cannot_be_marked_on_ride() {
        Rider rider = newRider();
        Driver driver = onlineDrivers(1).get(0);
        UUID rideId = newRequestedRide(rider);
        dispatchService.startDispatch(rideId);

        // Driver slips OFFLINE after the offer went out: claim will win, markOnRide will not.
        inTx(() -> driverRepository.updateStatus(driver.getId(), DriverStatus.OFFLINE.name()));

        dispatchService.handleDriverAccept(driver.getWhatsappId(), rideId);

        Ride ride = rideRepository.findById(rideId).orElseThrow();
        assertEquals(RideStatus.BROADCASTING, ride.getStatus(), "compensated back to BROADCASTING");
        assertNull(ride.getDriverId(), "assignment nulled out");
        assertNull(ride.getAssignedAt());
        RideOffer offer = offerRepository.findByRideIdAndDriverId(rideId, driver.getId()).orElseThrow();
        assertNull(offer.getResponse(), "offer not marked ACCEPTED on the compensated path");
    }

    // ---- gate 6: zero available drivers --------------------------------------

    @Test
    void zero_available_drivers_expires_immediately_and_notifies_rider() {
        Rider rider = newRider();
        UUID rideId = newRequestedRide(rider);

        dispatchService.startDispatch(rideId);

        assertEquals(RideStatus.EXPIRED, status(rideId));
        assertEquals(0, offerRepository.findByRideId(rideId).size());
        assertTrue(gateway.recorded().stream().anyMatch(m ->
                        m.recipient().equals(rider.getPhone())
                                && m.text().equals(MessageCatalog.noDrivers())),
                "NoDriversAvailable notification emitted");
    }

    // ---- helpers --------------------------------------------------------------

    private Rider newRider() {
        return riderRepository.save(new Rider("Rider " + suffix(), "+91" + digits(10)));
    }

    private List<Driver> onlineDrivers(int n) {
        return IntStream.range(0, n).mapToObj(i -> {
            Driver d = driverRepository.save(new Driver(
                    "Driver " + suffix(), "+91" + digits(10), "KA-01-" + digits(4),
                    DriverStatus.OFFLINE, true));
            driverAvailability.goOnline(d.getId());
            driverAvailability.recordInbound(d.getId()); // open 24h session for offers
            return d;
        }).toList();
    }

    private UUID newRequestedRide(Rider rider) {
        return rideRepository.save(new Ride(rider.getId(), "Main Gate", "Jetty")).getId();
    }

    private void forceRoundExpired(UUID rideId) {
        jdbc.update("UPDATE rides SET current_round_expires_at = now() - interval '1 second' WHERE id = ?",
                rideId);
    }

    private RideStatus status(UUID rideId) {
        return rideRepository.findById(rideId).orElseThrow().getStatus();
    }

    private void inTx(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> work.run());
    }

    private static String digits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    private static String suffix() {
        return digits(6);
    }
}
