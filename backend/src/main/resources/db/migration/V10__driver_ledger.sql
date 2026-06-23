CREATE TABLE driver_ledger (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id  UUID         NOT NULL REFERENCES drivers(id),
    ride_id    UUID         NULL     REFERENCES rides(id),
    type       VARCHAR(32)  NOT NULL CHECK (type IN ('EARNING','CANCELLATION_PENALTY','ADJUSTMENT','WITHDRAWAL')),
    amount     NUMERIC(10,2) NOT NULL,
    note       VARCHAR(255) NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_driver_id ON driver_ledger(driver_id);
CREATE INDEX idx_ledger_ride_id   ON driver_ledger(ride_id) WHERE ride_id IS NOT NULL;
