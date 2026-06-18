package com.autodispatch.rider.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal HS256 JWT for rider sessions: sub = rider id, 30-day expiry.
 * Secret comes from the environment (JWT_SECRET); constant-time signature
 * comparison on verify. Deliberately dependency-free.
 */
@Component
class JwtCodec {

    static final Duration TTL = Duration.ofDays(30);
    private static final String HEADER_B64 = base64Url(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final ObjectMapper objectMapper;

    JwtCodec(@Value("${autodispatch.jwt.secret}") String secret, ObjectMapper objectMapper) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("autodispatch.jwt.secret (JWT_SECRET) must be set");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    String issue(UUID riderId) {
        long now = Instant.now().getEpochSecond();
        String payload = "{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}"
                .formatted(riderId, now, now + TTL.toSeconds());
        String signingInput = HEADER_B64 + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(hmac(signingInput));
    }

    Optional<UUID> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] provided = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, provided)) {
                return Optional.empty();
            }
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (payload.path("exp").asLong(0) < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(payload.path("sub").stringValue()));
        } catch (Exception invalid) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
