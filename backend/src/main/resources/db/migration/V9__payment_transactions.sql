CREATE TABLE payment_transactions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id          UUID        NOT NULL REFERENCES rides(id),
    rider_id         UUID        NOT NULL REFERENCES riders(id),
    type             VARCHAR(20) NOT NULL CHECK (type IN ('RIDE_FARE','CANCELLATION_FEE','REFUND')),
    method           VARCHAR(16) NOT NULL DEFAULT 'CASH'
                                CHECK (method IN ('CASH','UPI','WALLET')),
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','COLLECTED','FAILED','REFUNDED')),
    amount           NUMERIC(10,2) NOT NULL,
    acknowledged_at  TIMESTAMPTZ NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_tx_ride_type UNIQUE (ride_id, type)
);
CREATE INDEX idx_payment_tx_ride_id  ON payment_transactions(ride_id);
CREATE INDEX idx_payment_tx_rider_id ON payment_transactions(rider_id);
