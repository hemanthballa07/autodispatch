package com.autodispatch.notification.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp Cloud API configuration. All values come from environment
 * variables (see .env.example). Secrets must never be hardcoded, written to
 * disk, or logged — do not add a toString that exposes them.
 */
@ConfigurationProperties(prefix = "whatsapp")
record WhatsAppProperties(
        Mode mode,
        String apiBaseUrl,
        String accessToken,
        String appSecret,
        String phoneNumberId,
        String verifyToken,
        boolean templateFallbackEnabled) {

    enum Mode {
        STUB,
        LIVE
    }

    @Override
    public String toString() {
        return "WhatsAppProperties[mode=%s, apiBaseUrl=%s, secrets=<redacted>]"
                .formatted(mode, apiBaseUrl);
    }
}
