package com.autodispatch.safety.internal;

import com.autodispatch.safety.api.SafetyEventView;
import com.autodispatch.safety.api.SafetyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rides/{rideId}/safety")
class SafetyController {

    private final SafetyService safetyService;

    SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @PostMapping("/sos")
    @ResponseStatus(HttpStatus.CREATED)
    SafetyEventView sos(@PathVariable UUID rideId,
                        @RequestParam(required = false) String details,
                        @RequestAttribute("riderId") UUID riderId) {
        return safetyService.triggerSos(rideId, riderId, details);
    }

    @PostMapping("/incident")
    @ResponseStatus(HttpStatus.CREATED)
    SafetyEventView incident(@PathVariable UUID rideId,
                              @RequestParam(required = false) String details,
                              @RequestAttribute("riderId") UUID riderId) {
        return safetyService.reportIncident(rideId, riderId, details);
    }
}
