package com.autodispatch.fare.internal;

import com.autodispatch.TestcontainersConfiguration;
import com.autodispatch.fare.api.FareService;
import com.autodispatch.fare.api.LocationCatalog;
import com.autodispatch.fare.api.LocationView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch coverage for {@link DefaultFareService} — both the {@link FareService}
 * estimate paths and the {@link LocationCatalog} lookups. Runs against the
 * Flyway-seeded location/fare-rule matrix (V5) and the seeded "Auto" vehicle
 * type (V7). All writes happen inside the test transaction and are rolled back.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FareServiceTest {

    @Autowired
    private FareService fareService;

    @Autowired
    private LocationCatalog locationCatalog;

    @Autowired
    private JdbcTemplate jdbc;

    // ---- FareService.estimate ------------------------------------------------

    @Test
    void estimate_returns_seeded_amount_for_active_zone_pair() {
        // Main gate (CORE) -> Library (ACADEMIC) is 30.00 in the seed matrix.
        Optional<BigDecimal> amount = fareService.estimate(loc("Main gate"), loc("Library"), null);

        assertTrue(amount.isPresent());
        assertEquals(0, amount.get().compareTo(new BigDecimal("30.00")));
    }

    @Test
    void estimate_returns_empty_for_unknown_pickup() {
        assertTrue(fareService.estimate(UUID.randomUUID(), loc("Library"), null).isEmpty());
    }

    @Test
    void estimate_returns_empty_for_unknown_drop() {
        assertTrue(fareService.estimate(loc("Main gate"), UUID.randomUUID(), null).isEmpty());
    }

    @Test
    void estimate_returns_empty_when_pickup_is_inactive() {
        UUID inactivePickup = insertLocation("Closed Gate " + suffix(), "CORE", false);

        assertTrue(fareService.estimate(inactivePickup, loc("Library"), null).isEmpty(),
                "an inactive pickup must not produce a quote");
    }

    @Test
    void estimate_returns_empty_when_drop_is_inactive() {
        UUID inactiveDrop = insertLocation("Closed Library " + suffix(), "ACADEMIC", false);

        assertTrue(fareService.estimate(loc("Main gate"), inactiveDrop, null).isEmpty(),
                "an inactive drop must not produce a quote");
    }

    @Test
    void estimate_returns_empty_when_no_fare_rule_covers_zone_pair() {
        // A brand-new zone with no fare_rules row for any pairing with CORE.
        UUID isolated = insertLocation("Isolated Stop " + suffix(), "ISOLATED_ZONE", true);

        assertTrue(fareService.estimate(loc("Main gate"), isolated, null).isEmpty(),
                "no fare rule for the zone pair must yield empty (caller maps to 422)");
    }

    @Test
    void estimate_with_matching_vehicle_type_returns_amount() {
        UUID autoTypeId = jdbc.queryForObject(
                "SELECT id FROM vehicle_types WHERE name = 'Auto'", UUID.class);

        Optional<BigDecimal> amount = fareService.estimate(loc("Main gate"), loc("Library"), autoTypeId);

        assertTrue(amount.isPresent());
        assertEquals(0, amount.get().compareTo(new BigDecimal("30.00")));
    }

    @Test
    void estimate_with_unknown_vehicle_type_returns_empty() {
        assertTrue(fareService.estimate(loc("Main gate"), loc("Library"), UUID.randomUUID()).isEmpty(),
                "no fare rule exists for an unknown vehicle type");
    }

    @Test
    void two_arg_overload_ignores_vehicle_type_and_returns_amount() {
        Optional<BigDecimal> amount = fareService.estimate(loc("Main gate"), loc("Library"));

        assertTrue(amount.isPresent());
        assertEquals(0, amount.get().compareTo(new BigDecimal("30.00")));
    }

    // ---- LocationCatalog -----------------------------------------------------

    @Test
    void active_locations_excludes_inactive_and_is_ordered_by_name() {
        insertLocation("zzz Hidden Stop " + suffix(), "CORE", false);

        List<LocationView> locations = locationCatalog.activeLocations();

        assertTrue(locations.stream().allMatch(LocationView::active),
                "activeLocations must only return active rows");
        assertFalse(locations.stream().anyMatch(l -> l.name().startsWith("zzz Hidden Stop")),
                "inactive locations must be excluded");

        List<String> names = locations.stream().map(LocationView::name).toList();
        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names, "locations must be ordered by name ascending");
    }

    @Test
    void find_by_id_returns_view_for_existing_location() {
        UUID mainGate = loc("Main gate");

        Optional<LocationView> view = locationCatalog.findById(mainGate);

        assertTrue(view.isPresent());
        assertEquals("Main gate", view.get().name());
        assertEquals("CORE", view.get().zone());
        assertTrue(view.get().active());
    }

    @Test
    void find_by_id_returns_empty_for_unknown_id() {
        assertTrue(locationCatalog.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void find_by_id_returns_inactive_locations_too() {
        UUID inactive = insertLocation("Retired Stop " + suffix(), "CORE", false);

        Optional<LocationView> view = locationCatalog.findById(inactive);

        assertTrue(view.isPresent(), "findById does not filter on active");
        assertFalse(view.get().active());
    }

    // ---- helpers -------------------------------------------------------------

    private UUID loc(String name) {
        return jdbc.queryForObject("SELECT id FROM locations WHERE name = ?", UUID.class, name);
    }

    private UUID insertLocation(String name, String zone, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO locations (id, name, zone, active) VALUES (?,?,?,?)",
                id, name, zone, active);
        return id;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
