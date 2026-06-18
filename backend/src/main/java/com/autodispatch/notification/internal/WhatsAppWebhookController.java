package com.autodispatch.notification.internal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executor;

/**
 * Meta webhook endpoint. Thin by design: verify, parse, claim idempotency,
 * hand off — zero business rules. Returns 200 fast on any validly signed
 * payload.
 */
@RestController
class WhatsAppWebhookController {

    private final WhatsAppProperties properties;
    private final WhatsAppPayloadParser payloadParser;
    private final WebhookDedupStore dedupStore;
    private final WebhookProcessor processor;
    private final Executor executor;

    WhatsAppWebhookController(WhatsAppProperties properties,
                              WhatsAppPayloadParser payloadParser,
                              WebhookDedupStore dedupStore,
                              WebhookProcessor processor,
                              @Qualifier("whatsappWebhookExecutor") Executor executor) {
        this.properties = properties;
        this.payloadParser = payloadParser;
        this.dedupStore = dedupStore;
        this.processor = processor;
        this.executor = executor;
    }

    /** Meta verification handshake. */
    @GetMapping("/webhooks/whatsapp")
    ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                  @RequestParam(name = "hub.verify_token", required = false) String token,
                                  @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String expected = properties.verifyToken();
        if ("subscribe".equals(mode) && expected != null && !expected.isBlank() && token != null
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/webhooks/whatsapp")
    ResponseEntity<String> receive(@RequestBody String rawBody,
                                   @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!signatureValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        for (WhatsAppPayloadParser.InboundMessage message : payloadParser.parse(rawBody)) {
            // Idempotency: duplicate deliveries are acknowledged, never reprocessed.
            if (dedupStore.claim(message.messageId())) {
                executor.execute(() -> processor.process(message));
            }
        }
        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    private boolean signatureValid(String rawBody, String header) {
        String secret = properties.appSecret();
        if (secret == null || secret.isBlank() || header == null || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] provided = HexFormat.of().parseHex(header.substring("sha256=".length()));
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }
}
