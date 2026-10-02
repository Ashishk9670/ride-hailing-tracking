CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE trips (
    id           UUID PRIMARY KEY,
    rider_id     VARCHAR(64)      NOT NULL,
    driver_id    VARCHAR(64),
    pickup_lat   DOUBLE PRECISION NOT NULL,
    pickup_lon   DOUBLE PRECISION NOT NULL,
    dropoff_lat  DOUBLE PRECISION NOT NULL,
    dropoff_lon  DOUBLE PRECISION NOT NULL,
    status       VARCHAR(32)      NOT NULL,
    created_at   TIMESTAMPTZ      NOT NULL,
    updated_at   TIMESTAMPTZ      NOT NULL,
    pickup_point GEOGRAPHY(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(pickup_lon, pickup_lat), 4326)::geography) STORED,
    dropoff_point GEOGRAPHY(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(dropoff_lon, dropoff_lat), 4326)::geography) STORED
);
CREATE INDEX idx_trips_driver_status ON trips (driver_id, status);
CREATE INDEX idx_trips_pickup_gist ON trips USING GIST (pickup_point);

CREATE TABLE location_history (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     UUID REFERENCES trips (id),
    driver_id   VARCHAR(64) NOT NULL,
    location    GEOGRAPHY(Point, 4326) NOT NULL,
    heading     DOUBLE PRECISION,
    speed_kmh   DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_lh_trip_time ON location_history (trip_id, recorded_at);
CREATE INDEX idx_lh_location_gist ON location_history USING GIST (location);
