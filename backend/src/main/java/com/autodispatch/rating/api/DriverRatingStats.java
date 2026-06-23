package com.autodispatch.rating.api;

import java.math.BigDecimal;
import java.util.UUID;

public record DriverRatingStats(UUID driverId, BigDecimal averageStars, long totalRatings) {}
