package com.autodispatch.rider.internal;

import com.autodispatch.common.api.PhoneNumbers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController {

    record SessionRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 20) String phone) {
    }

    record SessionResponse(String token, UUID riderId, String name) {
    }

    private final RiderRepository riderRepository;
    private final JwtCodec jwtCodec;

    SessionController(RiderRepository riderRepository, JwtCodec jwtCodec) {
        this.riderRepository = riderRepository;
        this.jwtCodec = jwtCodec;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    SessionResponse create(@Valid @RequestBody SessionRequest request) {
        String phone = PhoneNumbers.toE164(request.phone());
        Rider rider = riderRepository.findByPhone(phone)
                .orElseGet(() -> riderRepository.save(new Rider(request.name().trim(), phone)));
        if (!rider.getName().equals(request.name().trim())) {
            rider.updateName(request.name().trim());
        }
        return new SessionResponse(jwtCodec.issue(rider.getId()), rider.getId(), rider.getName());
    }
}
