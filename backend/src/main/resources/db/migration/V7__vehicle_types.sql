-- V7: vehicle types catalog (Phase 10, irreversible DDL: fare_rules PK extended to 3-column).
-- Note: no external FK references to fare_rules exist (verified from V5).

CREATE TABLE vehicle_types (
    id     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name   VARCHAR(60) NOT NULL UNIQUE,
    active BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Seed the only supported type initially.
INSERT INTO vehicle_types (name) VALUES ('Auto');

-- Drivers may be assigned a vehicle type (nullable until updated via admin).
ALTER TABLE drivers
    ADD COLUMN vehicle_type_id UUID REFERENCES vehicle_types(id);

-- Extend fare_rules to be per vehicle type.
ALTER TABLE fare_rules
    ADD COLUMN vehicle_type_id UUID REFERENCES vehicle_types(id);

-- Backfill: all existing fare rules belong to the Auto type.
UPDATE fare_rules
   SET vehicle_type_id = (SELECT id FROM vehicle_types WHERE name = 'Auto');

ALTER TABLE fare_rules
    ALTER COLUMN vehicle_type_id SET NOT NULL;

-- Change PK from (pickup_zone, drop_zone) to (pickup_zone, drop_zone, vehicle_type_id).
-- This is irreversible: revert requires a manual migration.
ALTER TABLE fare_rules DROP CONSTRAINT pk_fare_rules;
ALTER TABLE fare_rules ADD PRIMARY KEY (pickup_zone, drop_zone, vehicle_type_id);
