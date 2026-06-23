package com.autodispatch.fare.internal;

import com.autodispatch.fare.api.FareService;
import com.autodispatch.fare.api.LocationCatalog;
import com.autodispatch.fare.api.LocationView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class DefaultFareService implements FareService, LocationCatalog {

    private final LocationRepository locationRepository;
    private final JdbcTemplate jdbc;

    DefaultFareService(LocationRepository locationRepository, JdbcTemplate jdbc) {
        this.locationRepository = locationRepository;
        this.jdbc = jdbc;
    }

    @Override
    public List<LocationView> activeLocations() {
        return locationRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(DefaultFareService::toView)
                .toList();
    }

    @Override
    public Optional<LocationView> findById(UUID locationId) {
        return locationRepository.findById(locationId).map(DefaultFareService::toView);
    }

    @Override
    public Optional<BigDecimal> estimate(UUID pickupLocationId, UUID dropLocationId, UUID vehicleTypeId) {
        Optional<Location> pickup = locationRepository.findById(pickupLocationId).filter(Location::isActive);
        Optional<Location> drop = locationRepository.findById(dropLocationId).filter(Location::isActive);
        if (pickup.isEmpty() || drop.isEmpty()) {
            return Optional.empty();
        }
        List<BigDecimal> amounts;
        if (vehicleTypeId != null) {
            amounts = jdbc.queryForList("""
                    SELECT amount FROM fare_rules
                     WHERE pickup_zone = ? AND drop_zone = ? AND vehicle_type_id = ?
                    """, BigDecimal.class, pickup.get().getZone(), drop.get().getZone(), vehicleTypeId);
        } else {
            amounts = jdbc.queryForList("""
                    SELECT amount FROM fare_rules
                     WHERE pickup_zone = ? AND drop_zone = ?
                    """, BigDecimal.class, pickup.get().getZone(), drop.get().getZone());
        }
        return amounts.stream().findFirst();
    }

    private static LocationView toView(Location location) {
        return new LocationView(location.getId(), location.getName(), location.getZone(), location.isActive());
    }
}
