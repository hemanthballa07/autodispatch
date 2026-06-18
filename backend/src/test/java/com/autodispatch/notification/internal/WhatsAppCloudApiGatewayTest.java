package com.autodispatch.notification.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Phase 4 gate 6 (unit half): the LIVE gateway retries exactly once on a 500
 * and then swallows the failure — it never throws, so a send failure can
 * never roll back or block a domain transition.
 */
class WhatsAppCloudApiGatewayTest {

    private static WireMockServer wireMock;
    private WhatsAppCloudApiGateway gateway;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        gateway = new WhatsAppCloudApiGateway(new WhatsAppProperties(
                WhatsAppProperties.Mode.LIVE, wireMock.baseUrl(),
                "test-access-token", "test-app-secret", "12345", "test-verify-token", false));
    }

    @Test
    void send_failure_is_retried_once_then_swallowed() {
        wireMock.stubFor(post(urlPathEqualTo("/12345/messages"))
                .willReturn(aResponse().withStatus(500)));

        assertDoesNotThrow(() -> gateway.sendText("+919800000000", "hello"));

        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/12345/messages")));
    }

    @Test
    void successful_send_posts_once_with_bearer_auth() {
        wireMock.stubFor(post(urlPathEqualTo("/12345/messages"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"messages\":[{\"id\":\"wamid.out-1\"}]}")));

        gateway.sendText("+919800000000", "hello");

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/12345/messages"))
                .withHeader("Authorization", equalTo("Bearer test-access-token")));
    }
}
