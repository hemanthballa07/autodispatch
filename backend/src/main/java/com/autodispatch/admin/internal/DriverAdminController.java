package com.autodispatch.admin.internal;

import com.autodispatch.driver.api.DriverAdminService;
import com.autodispatch.driver.api.DriverAvailabilityService;
import com.autodispatch.driver.api.DriverSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/drivers")
class DriverAdminController {

    private final DriverAdminService driverAdminService;
    private final DriverAvailabilityService driverAvailabilityService;

    DriverAdminController(DriverAdminService driverAdminService,
                          DriverAvailabilityService driverAvailabilityService) {
        this.driverAdminService = driverAdminService;
        this.driverAvailabilityService = driverAvailabilityService;
    }

    @PostMapping("/")
    ResponseEntity<DriverAdminResponse> register(@Valid @RequestBody RegisterDriverRequest req) {
        DriverSummary summary = driverAdminService.register(req.name(), req.whatsappId(), req.vehicleNo());
        return ResponseEntity.status(201).body(toResponse(summary));
    }

    @GetMapping("/")
    List<DriverAdminResponse> listAll() {
        return driverAdminService.listAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    DriverAdminResponse findById(@PathVariable UUID id) {
        return driverAvailabilityService.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AdminNotFoundException("Driver not found: " + id));
    }

    @PostMapping("/{id}/verify")
    DriverAdminResponse verify(@PathVariable UUID id) {
        driverAvailabilityService.findById(id)
                .orElseThrow(() -> new AdminNotFoundException("Driver not found: " + id));
        return toResponse(driverAdminService.verify(id));
    }

    @PostMapping("/{id}/suspend")
    DriverAdminResponse suspend(@PathVariable UUID id) {
        driverAvailabilityService.findById(id)
                .orElseThrow(() -> new AdminNotFoundException("Driver not found: " + id));
        return toResponse(driverAdminService.suspend(id));
    }

    @PostMapping("/{id}/unsuspend")
    DriverAdminResponse unsuspend(@PathVariable UUID id) {
        driverAvailabilityService.findById(id)
                .orElseThrow(() -> new AdminNotFoundException("Driver not found: " + id));
        return toResponse(driverAdminService.unsuspend(id));
    }

    private DriverAdminResponse toResponse(DriverSummary s) {
        return new DriverAdminResponse(s.id(), s.name(), s.whatsappId(), s.vehicleNo(),
                s.verified(), s.suspended(), s.state().name(), s.lastInboundAt(), s.createdAt());
    }

    record RegisterDriverRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 32) String whatsappId,
            @NotBlank @Size(max = 20) String vehicleNo) {
    }

    record DriverAdminResponse(
            UUID id,
            String name,
            String whatsappId,
            String vehicleNo,
            boolean verified,
            boolean suspended,
            String state,
            Instant lastInboundAt,
            Instant createdAt) {
    }
}
