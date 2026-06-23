package com.autodispatch.rating.internal;

import com.autodispatch.rating.api.RatingService;
import com.autodispatch.rating.api.RatingView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rides/{rideId}/rating")
class RatingController {

    private final RatingService ratingService;

    RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RatingView rate(@PathVariable UUID rideId,
                    @RequestParam int stars,
                    @RequestParam(required = false) String comment,
                    @RequestAttribute("riderId") UUID riderId) {
        return ratingService.rateDriver(rideId, riderId, stars, comment);
    }

    @GetMapping
    ResponseEntity<RatingView> get(@PathVariable UUID rideId,
                                   @RequestAttribute("riderId") UUID riderId) {
        return ratingService.findByRideAndRider(rideId, riderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
