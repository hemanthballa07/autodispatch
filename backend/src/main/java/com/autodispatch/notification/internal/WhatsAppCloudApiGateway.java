package com.autodispatch.notification.internal;

import com.autodispatch.notification.api.MessageCatalog;
import com.autodispatch.notification.api.RideOfferNotification;
import com.autodispatch.notification.api.WhatsAppGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LIVE-mode WhatsApp Cloud API client (registered only when
 * whatsapp.mode=LIVE). Send failures retry once, then are logged and dropped:
 * a notification failure must NEVER roll back or block a domain transition,
 * so this class never throws. Credentials are never logged.
 */
class WhatsAppCloudApiGateway implements WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiGateway.class);

    private final WhatsAppProperties properties;
    private final RestClient restClient;

    WhatsAppCloudApiGateway(WhatsAppProperties properties) {
        this.properties = properties;
        // HTTP/1.1 explicitly: the JDK client's default h2 upgrade is not
        // needed for the Cloud API and breaks against test doubles.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Override
    public void sendText(String recipientPhoneE164, String text) {
        send(Map.of(
                "messaging_product", "whatsapp",
                "to", toWaId(recipientPhoneE164),
                "type", "text",
                "text", Map.of("body", text)));
    }

    @Override
    public void sendRideOffer(String recipientPhoneE164, RideOfferNotification offer) {
        Map<String, Object> interactive = Map.of(
                "type", "button",
                "body", Map.of("text", MessageCatalog.rideOffer(offer)),
                "action", Map.of("buttons", List.of(Map.of(
                        "type", "reply",
                        "reply", Map.of(
                                "id", "ACCEPT:" + offer.rideId(),
                                "title", "Accept")))));
        send(Map.of(
                "messaging_product", "whatsapp",
                "to", toWaId(recipientPhoneE164),
                "type", "interactive",
                "interactive", interactive));
    }

    private void send(Map<String, Object> payload) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                restClient.post()
                        .uri("/{phoneNumberId}/messages", properties.phoneNumberId())
                        .header("Authorization", "Bearer " + properties.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (Exception e) {
                if (attempt == 2) {
                    log.error("NOTIFICATION_FAILED: WhatsApp send failed after retry (type={})",
                            payload.get("type"), e);
                }
            }
        }
    }

    private static String toWaId(String phoneE164) {
        return phoneE164.startsWith("+") ? phoneE164.substring(1) : phoneE164;
    }
}
