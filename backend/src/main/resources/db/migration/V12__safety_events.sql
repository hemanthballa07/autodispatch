CREATE TABLE safety_events (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id    UUID          NOT NULL REFERENCES rides(id),
    rider_id   UUID          NOT NULL REFERENCES riders(id),
    type       VARCHAR(32)   NOT NULL CHECK (type IN ('SOS','INCIDENT_REPORT')),
    details    VARCHAR(1000) NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_safety_ride_id  ON safety_events(ride_id);
CREATE INDEX idx_safety_rider_id ON safety_events(rider_id);
