package com.autodispatch.driver.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.driver.api.DriverOnRideException;
import com.autodispatch.driver.api.DriverSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Normal-path state/Redis transitions for DefaultDriverAvailabilityService.
 * The markOnRide conditional-update race is covered separately by
 * DriverMarkOnRideConcurrencyTest. Deliberately not @Transactional: several
 * DriverRepository methods are native @Modifying updates without
 * clearAutomatically, so a shared test transaction/persistence context would
 * read stale cached entities after those writes (matches the no-@Transactional
 * pattern already used by AdminApiIntegrationTest and
 * DriverMarkOnRideConcurrencyTest for the same reason).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DefaultDriverAvailabilityServiceTest {

    @Autowired
    private DriverAvailabilityService availability;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearRedisMirror() {
        redis.delete(DefaultDriverAvailabilityService.AVAILABLE_SET_KEY);
    }

    @Test
    void goOnline_moves_offline_driver_to_available_and_adds_to_redis() {
        UUID id = seed(DriverStatus.OFFLINE, true).getId();

        availability.goOnline(id);

        assertEquals(DriverStatus.AVAILABLE, driverRepository.findById(id).orElseThrow().getStatus());
        assertTrue(isInRedisSet(id));
    }

    @Test
    void goOnline_rejected_when_driver_on_ride() {
        UUID id = seed(DriverStatus.ON_RIDE, true).getId();

        assertThrows(DriverOnRideException.class, () -> availability.goOnline(id));
        assertEquals(DriverStatus.ON_RIDE, driverRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void goOffline_moves_available_driver_to_offline_and_removes_from_redis() {
        UUID id = seed(DriverStatus.AVAILABLE, true).getId();
        availability.goOnline(id); // ensures the Redis mirror holds the member before removal

        availability.goOffline(id);

        assertEquals(DriverStatus.OFFLINE, driverRepository.findById(id).orElseThrow().getStatus());
        assertFalse(isInRedisSet(id));
    }

    @Test
    void goOffline_rejected_when_driver_on_ride() {
        UUID id = seed(DriverStatus.ON_RIDE, true).getId();

        assertThrows(DriverOnRideException.class, () -> availability.goOffline(id));
        assertEquals(DriverStatus.ON_RIDE, driverRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void recordInbound_stamps_last_inbound_at() {
        UUID id = seed(DriverStatus.OFFLINE, true).getId();
        assertNull(driverRepository.findById(id).orElseThrow().getLastInboundAt());

        availability.recordInbound(id);

        assertNotNull(driverRepository.findById(id).orElseThrow().getLastInboundAt());
    }

    @Test
    void tryMarkOnRide_wins_for_available_driver_and_removes_from_redis() {
        UUID id = seed(DriverStatus.AVAILABLE, true).getId();
        availability.goOnline(id);

        boolean won = availability.tryMarkOnRide(id);

        assertTrue(won);
        assertEquals(DriverStatus.ON_RIDE, driverRepository.findById(id).orElseThrow().getStatus());
        assertFalse(isInRedisSet(id));
    }

    @Test
    void tryMarkOnRide_fails_for_non_available_driver() {
        UUID id = seed(DriverStatus.OFFLINE, true).getId();

        boolean won = availability.tryMarkOnRide(id);

        assertFalse(won);
        assertEquals(DriverStatus.OFFLINE, driverRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void makeAvailable_sets_status_available_and_adds_to_redis() {
        UUID id = seed(DriverStatus.ON_RIDE, true).getId();

        availability.makeAvailable(id);

        assertEquals(DriverStatus.AVAILABLE, driverRepository.findById(id).orElseThrow().getStatus());
        assertTrue(isInRedisSet(id));
    }

    @Test
    void listAvailableVerified_repairs_redis_mirror_against_db() {
        Driver dbOnly = seed(DriverStatus.AVAILABLE, true); // AVAILABLE in DB, absent from Redis
        String staleId = UUID.randomUUID().toString(); // present in Redis, absent from DB
        redis.opsForSet().add(DefaultDriverAvailabilityService.AVAILABLE_SET_KEY, staleId);

        List<DriverSummary> result = availability.listAvailableVerified();

        assertTrue(result.stream().anyMatch(s -> s.id().equals(dbOnly.getId())));
        assertTrue(isInRedisSet(dbOnly.getId()), "DB-only member must be added to the Redis mirror");
        assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(
                        DefaultDriverAvailabilityService.AVAILABLE_SET_KEY, staleId)),
                "stale Redis member with no DB counterpart must be dropped");
    }

    @Test
    void listAvailableVerified_excludes_unverified_and_suspended_drivers() {
        Driver unverified = seed(DriverStatus.AVAILABLE, false);
        Driver suspended = seed(DriverStatus.AVAILABLE, true);
        jdbc.update("UPDATE drivers SET suspended = true WHERE id = ?", suspended.getId());

        List<DriverSummary> result = availability.listAvailableVerified();

        assertTrue(result.stream().noneMatch(s -> s.id().equals(unverified.getId())));
        assertTrue(result.stream().noneMatch(s -> s.id().equals(suspended.getId())));
    }

    @Test
    void findByWhatsappId_returns_summary_for_known_driver_and_empty_for_unknown() {
        Driver d = seed(DriverStatus.OFFLINE, true);

        Optional<DriverSummary> found = availability.findByWhatsappId(d.getWhatsappId());
        Optional<DriverSummary> missing = availability.findByWhatsappId("+910000000000");

        assertTrue(found.isPresent());
        assertEquals(d.getId(), found.get().id());
        assertTrue(missing.isEmpty());
    }

    @Test
    void findById_returns_summary_for_known_driver_and_empty_for_unknown() {
        Driver d = seed(DriverStatus.OFFLINE, true);

        assertTrue(availability.findById(d.getId()).isPresent());
        assertTrue(availability.findById(UUID.randomUUID()).isEmpty());
    }

    private Driver seed(DriverStatus status, boolean verified) {
        return driverRepository.save(new Driver(
                "Driver " + digits(5), "+9194" + digits(8), "KA-03-" + digits(4), status, verified));
    }

    private boolean isInRedisSet(UUID id) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(
                DefaultDriverAvailabilityService.AVAILABLE_SET_KEY, id.toString()));
    }

    private static String digits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }
}
