-- V5: rider flow — locations, fare rules, one-active-ride guarantee (Phase 5).

CREATE TABLE locations (
    id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name   VARCHAR(120) NOT NULL UNIQUE,
    zone   VARCHAR(40)  NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE fare_rules (
    pickup_zone VARCHAR(40)    NOT NULL,
    drop_zone   VARCHAR(40)    NOT NULL,
    amount      NUMERIC(10, 2) NOT NULL,
    CONSTRAINT pk_fare_rules PRIMARY KEY (pickup_zone, drop_zone)
);

-- Server-side double-tap / double-booking guard: at most one active ride per rider.
CREATE UNIQUE INDEX uq_rides_one_active_per_rider ON rides (rider_id)
    WHERE status IN ('REQUESTED', 'BROADCASTING', 'ASSIGNED', 'ARRIVED', 'IN_PROGRESS');

-- ~12 placeholder campus-area stops (names to be renamed via admin later).
INSERT INTO locations (name, zone) VALUES
    ('Hostel gate',      'HOSTELS'),
    ('Hostel block B',   'HOSTELS'),
    ('Mess junction',    'HOSTELS'),
    ('Academic block',   'ACADEMIC'),
    ('Library',          'ACADEMIC'),
    ('Labs complex',     'ACADEMIC'),
    ('Main gate',        'CORE'),
    ('Admin block',      'CORE'),
    ('Health centre',    'CORE'),
    ('Jetty',            'TRANSPORT'),
    ('Bus stand',        'TRANSPORT'),
    ('Railway station',  'TRANSPORT');

-- Symmetric placeholder fare matrix (amounts are placeholders).
INSERT INTO fare_rules (pickup_zone, drop_zone, amount) VALUES
    ('HOSTELS',   'HOSTELS',   20.00),
    ('ACADEMIC',  'ACADEMIC',  20.00),
    ('CORE',      'CORE',      20.00),
    ('TRANSPORT', 'TRANSPORT', 20.00),
    ('HOSTELS',   'ACADEMIC',  30.00),
    ('ACADEMIC',  'HOSTELS',   30.00),
    ('HOSTELS',   'CORE',      30.00),
    ('CORE',      'HOSTELS',   30.00),
    ('ACADEMIC',  'CORE',      30.00),
    ('CORE',      'ACADEMIC',  30.00),
    ('HOSTELS',   'TRANSPORT', 50.00),
    ('TRANSPORT', 'HOSTELS',   50.00),
    ('ACADEMIC',  'TRANSPORT', 50.00),
    ('TRANSPORT', 'ACADEMIC',  50.00),
    ('CORE',      'TRANSPORT', 40.00),
    ('TRANSPORT', 'CORE',      40.00);
