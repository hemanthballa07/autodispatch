package com.autodispatch.admin.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autodispatch.admin")
record AdminProperties(String apiKey) {
}
