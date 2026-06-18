package com.autodispatch.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling is on by default and switched off in tests
 * (autodispatch.sweeper.enabled=false), where sweeps are invoked directly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "autodispatch.sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
