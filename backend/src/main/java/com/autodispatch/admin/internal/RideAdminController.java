package com.autodispatch.admin.internal;

import com.autodispatch.dispatch.api.AdminRideQueries;
import com.autodispatch.dispatch.api.AdminRideView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/rides")
class RideAdminController {

    private final AdminRideQueries adminRideQueries;

    RideAdminController(AdminRideQueries adminRideQueries) {
        this.adminRideQueries = adminRideQueries;
    }

    @GetMapping("/")
    List<AdminRideView> listRides(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminRideQueries.listRides(status, date, page, size);
    }

    @GetMapping("/{id}")
    AdminRideView findById(@PathVariable UUID id) {
        return adminRideQueries.findRideById(id)
                .orElseThrow(() -> new AdminNotFoundException("Ride not found: " + id));
    }
}
