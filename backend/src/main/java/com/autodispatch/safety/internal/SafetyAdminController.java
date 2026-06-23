package com.autodispatch.safety.internal;

import com.autodispatch.safety.api.SafetyEventView;
import com.autodispatch.safety.api.SafetyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/rides/{rideId}/safety")
class SafetyAdminController {

    private final SafetyService safetyService;

    SafetyAdminController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @GetMapping
    List<SafetyEventView> list(@PathVariable UUID rideId) {
        return safetyService.findByRide(rideId);
    }
}
