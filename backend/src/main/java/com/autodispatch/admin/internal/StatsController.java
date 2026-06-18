package com.autodispatch.admin.internal;

import com.autodispatch.dispatch.api.AdminRideQueries;
import com.autodispatch.dispatch.api.RideStats;
import com.autodispatch.driver.api.DriverAvailabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/stats")
class StatsController {

    private final AdminRideQueries adminRideQueries;
    private final DriverAvailabilityService driverAvailabilityService;

    StatsController(AdminRideQueries adminRideQueries, DriverAvailabilityService driverAvailabilityService) {
        this.adminRideQueries = adminRideQueries;
        this.driverAvailabilityService = driverAvailabilityService;
    }

    @GetMapping
    StatsResponse stats() {
        RideStats rideStats = adminRideQueries.countStats();
        long availableDrivers = driverAvailabilityService.listAvailableVerified().size();
        return new StatsResponse(rideStats.activeRides(), rideStats.completedToday(), availableDrivers);
    }

    record StatsResponse(long activeRides, long completedToday, long availableDrivers) {
    }
}
