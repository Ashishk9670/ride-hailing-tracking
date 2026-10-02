package com.example.ridehailing.dto;

public record LocationUpdate(String driverId, String tripId, double lat, double lon,
                             double heading, double speedKmh, long timestamp) {
    public LocationUpdate withTripId(String id) {
        return new LocationUpdate(driverId, id, lat, lon, heading, speedKmh, timestamp);
    }
}
