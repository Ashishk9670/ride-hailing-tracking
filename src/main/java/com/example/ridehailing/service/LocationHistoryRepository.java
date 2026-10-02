package com.example.ridehailing.service;

import com.example.ridehailing.dto.LocationUpdate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostGIS access via native SQL. */
@Repository
public class LocationHistoryRepository {
    private final JdbcTemplate jdbc;

    public LocationHistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(LocationUpdate u) {
        jdbc.update("""
                INSERT INTO location_history (trip_id, driver_id, location, heading, speed_kmh, recorded_at)
                VALUES (?::uuid, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?)
                """,
                u.tripId(), u.driverId(), u.lon(), u.lat(), u.heading(), u.speedKmh(),
                Timestamp.from(Instant.ofEpochMilli(u.timestamp())));
    }

    public List<LocationUpdate> findByTrip(UUID tripId) {
        return jdbc.query("""
                SELECT driver_id, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon,
                       heading, speed_kmh, recorded_at
                FROM location_history WHERE trip_id = ? ORDER BY recorded_at, id
                """,
                (rs, i) -> new LocationUpdate(rs.getString("driver_id"), tripId.toString(),
                        rs.getDouble("lat"), rs.getDouble("lon"), rs.getDouble("heading"),
                        rs.getDouble("speed_kmh"), rs.getTimestamp("recorded_at").getTime()),
                tripId);
    }

    /** Route length in meters computed by PostGIS. */
    public double routeLengthMeters(UUID tripId) {
        Double d = jdbc.queryForObject("""
                SELECT COALESCE(ST_Length(ST_MakeLine(location::geometry ORDER BY recorded_at, id)::geography), 0)
                FROM location_history WHERE trip_id = ?
                """, Double.class, tripId);
        return d == null ? 0 : d;
    }
}
