package com.autodispatch.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis wiring for later use (driver availability tracking, dispatch locking).
 * Intentionally unused until the design phase: no key schemas or locking
 * semantics are defined yet.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate dispatchRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
