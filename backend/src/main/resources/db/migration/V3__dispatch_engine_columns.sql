-- V3: additive columns for the dispatch engine (Phase 3, locked design).

ALTER TABLE rides
    ADD COLUMN broadcast_round INT NOT NULL DEFAULT 0,
    ADD COLUMN current_round_expires_at TIMESTAMPTZ;

ALTER TABLE ride_offers
    ADD COLUMN round INT NOT NULL DEFAULT 1;
