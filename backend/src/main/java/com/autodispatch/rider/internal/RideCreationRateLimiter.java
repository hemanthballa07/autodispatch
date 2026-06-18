package com.autodispatch.rider.internal;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * In-app rate limit: max 3 ride creations per rider per 10 minutes, counted
 * in Redis (fixed window keyed per rider).
 */
@Component
class RideCreationRateLimiter {

    static final int MAX_CREATIONS = 3;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    RideCreationRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    boolean isLimited(UUID riderId) {
        String current = redis.opsForValue().get(key(riderId));
        return current != null && Long.parseLong(current) >= MAX_CREATIONS;
    }

    void recordCreation(UUID riderId) {
        Long count = redis.opsForValue().increment(key(riderId));
        if (count != null && count == 1) {
            redis.expire(key(riderId), WINDOW);
        }
    }

    private static String key(UUID riderId) {
        return "rate:ride-create:" + riderId;
    }
}
