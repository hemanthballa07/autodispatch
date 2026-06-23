CREATE TABLE ride_ratings (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id        UUID         NOT NULL REFERENCES rides(id),
    driver_id      UUID         NOT NULL REFERENCES drivers(id),
    rater_rider_id UUID         NOT NULL REFERENCES riders(id),
    driver_stars   INTEGER      NOT NULL CHECK (driver_stars BETWEEN 1 AND 5),
    comment        VARCHAR(500) NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_rating_ride_rider UNIQUE (ride_id, rater_rider_id)
);
CREATE INDEX idx_rating_ride_id   ON ride_ratings(ride_id);
CREATE INDEX idx_rating_driver_id ON ride_ratings(driver_id);
