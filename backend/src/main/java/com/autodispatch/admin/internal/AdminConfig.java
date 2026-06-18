package com.autodispatch.admin.internal;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminProperties.class)
class AdminConfig {

    private final AdminProperties properties;

    AdminConfig(AdminProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateApiKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "autodispatch.admin.api-key must not be blank — set ADMIN_API_KEY in the environment");
        }
    }
}
