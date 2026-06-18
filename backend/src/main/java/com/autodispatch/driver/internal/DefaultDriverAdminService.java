package com.autodispatch.driver.internal;

import com.autodispatch.driver.api.DriverAdminService;
import com.autodispatch.driver.api.DriverAlreadyExistsException;
import com.autodispatch.driver.api.DriverOnRideException;
import com.autodispatch.driver.api.DriverSummary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
class DefaultDriverAdminService implements DriverAdminService {

    private final DriverRepository driverRepository;
    private final StringRedisTemplate redis;

    DefaultDriverAdminService(DriverRepository driverRepository, StringRedisTemplate redis) {
        this.driverRepository = driverRepository;
        this.redis = redis;
    }

    @Override
    public DriverSummary register(String name, String whatsappId, String vehicleNo) {
        Driver driver = new Driver(name, whatsappId, vehicleNo, DriverStatus.OFFLINE, false);
        try {
            return DefaultDriverAvailabilityService.toSummary(driverRepository.saveAndFlush(driver));
        } catch (DataIntegrityViolationException e) {
            throw new DriverAlreadyExistsException(whatsappId);
        }
    }

    @Override
    public DriverSummary verify(UUID driverId) {
        driverRepository.verifyDriver(driverId);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
        return DefaultDriverAvailabilityService.toSummary(driver);
    }

    @Override
    public DriverSummary suspend(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
        if (driver.getStatus() == DriverStatus.ON_RIDE) {
            throw new DriverOnRideException(driverId, "suspend");
        }
        driverRepository.suspendDriver(driverId);
        redis.opsForSet().remove(DefaultDriverAvailabilityService.AVAILABLE_SET_KEY, driverId.toString());
        Driver updated = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
        return DefaultDriverAvailabilityService.toSummary(updated);
    }

    @Override
    public DriverSummary unsuspend(UUID driverId) {
        driverRepository.unsuspendDriver(driverId);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown driver: " + driverId));
        return DefaultDriverAvailabilityService.toSummary(driver);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverSummary> listAll() {
        return driverRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(DefaultDriverAvailabilityService::toSummary)
                .toList();
    }
}
