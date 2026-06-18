-- V4: WhatsApp adapter support (Phase 4, locked design).

-- Webhook idempotency: one row per processed WhatsApp message id.
CREATE TABLE processed_webhook_messages (
    message_id  VARCHAR(128) PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 24h session rule: offers go only to drivers whose last inbound is recent.
ALTER TABLE drivers
    ADD COLUMN last_inbound_at TIMESTAMPTZ;
