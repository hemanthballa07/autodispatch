-- V8: ride enrichment for Phase 10 (scheduled rides, vehicle type, location IDs, lifecycle timestamps).

ALTER TABLE rides
    ADD COLUMN vehicle_type_id    UUID REFERENCES vehicle_types(id),
    ADD COLUMN pickup_location_id UUID REFERENCES locations(id),
    ADD COLUMN drop_location_id   UUID REFERENCES locations(id),
    ADD COLUMN scheduled_for      TIMESTAMPTZ,
    ADD COLUMN cancelled_by       VARCHAR(16),
    ADD COLUMN arrived_at         TIMESTAMPTZ,
    ADD COLUMN started_at         TIMESTAMPTZ,
    ADD COLUMN final_amount       NUMERIC(10, 2);

-- Extend the status CHECK to allow the new SCHEDULED initial state.
ALTER TABLE rides DROP CONSTRAINT IF EXISTS rides_status_check;
ALTER TABLE rides ADD CONSTRAINT rides_status_check CHECK (status IN (
    'SCHEDULED', 'REQUESTED', 'BROADCASTING', 'ASSIGNED', 'ARRIVED',
    'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED'));

-- Sweeper index: find scheduled rides ready for release.
CREATE INDEX idx_rides_scheduled ON rides (scheduled_for)
    WHERE status = 'SCHEDULED';

-- CQRS stats query (Phase 10/3): completed rides per driver.
CREATE INDEX idx_rides_driver_completed ON rides (driver_id, completed_at)
    WHERE status = 'COMPLETED';

-- Update the one-active-ride guard to include SCHEDULED (prevents booking while a
-- future ride is already pending).
DROP INDEX uq_rides_one_active_per_rider;
CREATE UNIQUE INDEX uq_rides_one_active_per_rider ON rides (rider_id)
    WHERE status IN ('SCHEDULED', 'REQUESTED', 'BROADCASTING', 'ASSIGNED', 'ARRIVED', 'IN_PROGRESS');
