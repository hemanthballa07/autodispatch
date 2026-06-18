package com.autodispatch.notification.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Builders for Meta-shaped webhook payloads and HMAC signatures
 * (whatsapp.app-secret=test-app-secret in test properties).
 */
final class WebhookTestSupport {

    static final String TEST_APP_SECRET = "test-app-secret";

    private WebhookTestSupport() {
    }

    static String textPayload(String messageId, String fromWaId, String body) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{"value":{
                "messaging_product":"whatsapp",
                "messages":[{"from":"%s","id":"%s","timestamp":"1749600000","type":"text","text":{"body":"%s"}}]
                },"field":"messages"}]}]}
                """.formatted(fromWaId, messageId, body);
    }

    static String buttonPayload(String messageId, String fromWaId, String buttonId) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{"value":{
                "messaging_product":"whatsapp",
                "messages":[{"from":"%s","id":"%s","timestamp":"1749600000","type":"interactive",
                "interactive":{"type":"button_reply","button_reply":{"id":"%s","title":"Accept"}}}]
                },"field":"messages"}]}]}
                """.formatted(fromWaId, messageId, buttonId);
    }

    static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
