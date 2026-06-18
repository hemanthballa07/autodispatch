-- V2: domain tables for the dispatch system (Phase 2, locked design).

CREATE TABLE drivers (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    whatsapp_id VARCHAR(32)  NOT NULL UNIQUE,
    vehicle_no  VARCHAR(20)  NOT NULL,
    status      VARCHAR(16)  NOT NULL CHECK (status IN ('OFFLINE', 'AVAILABLE', 'ON_RIDE')),
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE riders (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    phone      VARCHAR(20)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE rides (
    id                UUID PRIMARY KEY,
    rider_id          UUID         NOT NULL REFERENCES riders (id),
    driver_id         UUID         REFERENCES drivers (id),
    pickup_label      VARCHAR(120) NOT NULL,
    drop_label        VARCHAR(120) NOT NULL,
    status            VARCHAR(16)  NOT NULL CHECK (status IN
                          ('REQUESTED', 'BROADCASTING', 'ASSIGNED', 'ARRIVED',
                           'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    fare_amount       NUMERIC(10, 2),
    requested_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    assigned_at       TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    cancel_reason     VARCHAR(255),
    rebroadcast_count INT          NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE ride_offers (
    id           UUID PRIMARY KEY,
    ride_id      UUID        NOT NULL REFERENCES rides (id),
    driver_id    UUID        NOT NULL REFERENCES drivers (id),
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    response     VARCHAR(16) CHECK (response IN ('ACCEPTED', 'IGNORED', 'TOO_LATE')),
    CONSTRAINT uq_ride_offers_ride_driver UNIQUE (ride_id, driver_id)
);

CREATE INDEX idx_rides_status ON rides (status);
CREATE INDEX idx_rides_rider_id ON rides (rider_id);
CREATE INDEX idx_ride_offers_ride_id ON ride_offers (ride_id);
CREATE INDEX idx_drivers_status_verified ON drivers (status, verified);
