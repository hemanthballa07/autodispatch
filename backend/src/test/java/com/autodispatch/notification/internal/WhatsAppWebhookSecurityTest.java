package com.autodispatch.notification.internal;

import com.autodispatch.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static com.autodispatch.notification.internal.WebhookTestSupport.sign;
import static com.autodispatch.notification.internal.WebhookTestSupport.textPayload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 gates 1–2: signature verification and the Meta handshake.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WhatsAppWebhookSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void valid_signature_is_accepted() throws Exception {
        String body = textPayload("wamid.sec-valid-001", "919812345678", "ON");
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void tampered_body_is_rejected_and_not_processed() throws Exception {
        String signedBody = textPayload("wamid.sec-tampered-001", "919812345678", "ON");
        String tamperedBody = textPayload("wamid.sec-tampered-001", "919812345678", "OFF");
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(signedBody))
                        .content(tamperedBody))
                .andExpect(status().isForbidden());

        Integer processed = jdbc.queryForObject(
                "SELECT count(*) FROM processed_webhook_messages WHERE message_id = ?",
                Integer.class, "wamid.sec-tampered-001");
        assertEquals(0, processed, "rejected body must not be processed");
    }

    @Test
    void missing_signature_header_is_rejected() throws Exception {
        String body = textPayload("wamid.sec-missing-001", "919812345678", "ON");
        mockMvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void handshake_returns_challenge_on_correct_token() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp")
                        .queryParam("hub.mode", "subscribe")
                        .queryParam("hub.verify_token", "test-verify-token")
                        .queryParam("hub.challenge", "challenge-1234"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-1234"));
    }

    @Test
    void handshake_rejects_wrong_token() throws Exception {
        mockMvc.perform(get("/webhooks/whatsapp")
                        .queryParam("hub.mode", "subscribe")
                        .queryParam("hub.verify_token", "wrong-token")
                        .queryParam("hub.challenge", "challenge-1234"))
                .andExpect(status().isForbidden());
    }
}
