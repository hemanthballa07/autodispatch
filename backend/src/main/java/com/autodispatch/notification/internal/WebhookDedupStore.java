package com.autodispatch.notification.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Webhook idempotency backed by processed_webhook_messages (unique message
 * id). Claiming is a single atomic INSERT … ON CONFLICT DO NOTHING.
 */
@Component
class WebhookDedupStore {

    private final JdbcTemplate jdbc;

    WebhookDedupStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true if this delivery is the first for the message id. */
    boolean claim(String messageId) {
        return jdbc.update("""
                INSERT INTO processed_webhook_messages (message_id)
                VALUES (?)
                ON CONFLICT (message_id) DO NOTHING
                """, messageId) == 1;
    }
}
