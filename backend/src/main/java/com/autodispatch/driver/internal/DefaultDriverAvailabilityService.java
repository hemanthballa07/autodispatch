package com.autodispatch.driver.internal;

import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.driver.api.DriverOnRideException;
import com.autodispatch.driver.api.DriverSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * DB is the source of truth; the Redis SET {@code drivers:available} is a
 * mirror. Every write goes DB first, then Redis. Reads repair the mirror
 * whenever it disagrees with the DB.
 */
@Service
@Transactional
class DefaultDriverAvailabilityService implements DriverAvailabilityService {

    static final String AVAILABLE_SET_KEY = "drivers:available";

    private final DriverRepository driverRepository;
    private final StringRedisTemplate redis;

    DefaultDriverAvailabilityService(DriverRepository driverRepository, StringRedisTemplate redis) {
        this.driverRepository = driverRepository;
        this.redis = redis;
    }

    @Override
    public void goOnline(UUID driverId) {
        Driver driver = requireDriver(driverId);
        if (driver.getStatus() == DriverStatus.ON_RIDE) {
            throw new DriverOnRideException(driverId, "goOnline");
        }
        driverRepository.updateStatus(driverId, DriverStatus.AVAILABLE.name());
        redis.opsForSet().add(AVAILABLE_SET_KEY, driverId.toString());
    }

    @Override
    public void goOffline(UUID driverId) {
        Driver driver = requireDriver(driverId);
        if (driver.getStatus() == DriverStatus.ON_RIDE) {
            throw new DriverOnRideException(driverId, "goOffline");
        }
        driverRepository.updateStatus(driverId, DriverStatus.OFFLINE.name());
        redis.opsForSet().remove(AVAILABLE_SET_KEY, driverId.toString());
    }

    @Override
    public List<DriverSummary> listAvailableVerified() {
        Set<String> mirror = redis.opsForSet().members(AVAILABLE_SET_KEY);
        Set<String> redisIds = mirror == null ? Set.of() : mirror;

        List<Driver> dbAvailable = driverRepository.findByStatus(DriverStatus.AVAILABLE);
        Set<String> dbIds = new HashSet<>();
        dbAvailable.forEach(d -> dbIds.add(d.getId().toString()));

        // DB wins: drop stale members, add missing ones.
        for (String stale : redisIds) {
            if (!dbIds.contains(stale)) {
                redis.opsForSet().remove(AVAILABLE_SET_KEY, stale);
            }
        }
        for (String missing : dbIds) {
            if (!redisIds.contains(missing)) {
                redis.opsForSet().add(AVAILABLE_SET_KEY, missing);
            }
        }

        return dbAvailable.stream()
                .filter(Driver::isVerified)
                .filter(d -> !d.isSuspended())
                .map(DefaultDriverAvailabilityService::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DriverSummary> findByWhatsappId(String whatsappId) {
        return driverRepository.findByWhatsappId(whatsappId)
                .map(DefaultDriverAvailabilityService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DriverSummary> findById(UUID driverId) {
        return driverRepository.findById(driverId)
                .map(DefaultDriverAvailabilityService::toSummary);
    }

    @Override
    public boolean tryMarkOnRide(UUID driverId) {
        boolean won = driverRepository.markOnRide(driverId) == 1;
        if (won) {
            redis.opsForSet().remove(AVAILABLE_SET_KEY, driverId.toString());
        }
        return won;
    }

    @Override
    public void makeAvailable(UUID driverId) {
        driverRepository.updateStatus(driverId, DriverStatus.AVAILABLE.name());
        redis.opsForSet().add(AVAILABLE_SET_KEY, driverId.toString());
    }

    @Override
    public void recordInbound(UUID driverId) {
        driverRepository.touchLastInbound(driverId);
    }

    private Driver requireDriver(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
    }

    static DriverSummary toSummary(Driver d) {
        return new DriverSummary(d.getId(), d.getName(), d.getWhatsappId(), d.getVehicleNo(),
                d.isVerified(), d.isSuspended(),
                com.autodispatch.driver.api.DriverState.valueOf(d.getStatus().name()),
                d.getLastInboundAt(), d.getCreatedAt());
    }
}
