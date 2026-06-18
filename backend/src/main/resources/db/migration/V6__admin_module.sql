-- suspended: distinguishes "never verified" from "deliberately revoked"
ALTER TABLE drivers
    ADD COLUMN suspended BOOLEAN NOT NULL DEFAULT FALSE;

-- enables date-range filtering on admin ride queries
CREATE INDEX idx_rides_requested_at ON rides (requested_at DESC);
