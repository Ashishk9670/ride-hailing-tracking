package com.example.ridehailing.dto;

import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripStatus;

public record TripView(String id, String riderId, String driverId, TripStatus status,
                       double pickupLat, double pickupLon, double dropoffLat, double dropoffLon,
                       double surgeMultiplier, double fareEstimate) {
    public static TripView of(Trip t) {
        return new TripView(t.getId().toString(), t.getRiderId(), t.getDriverId(), t.getStatus(),
                t.getPickupLat(), t.getPickupLon(), t.getDropoffLat(), t.getDropoffLon(),
                t.getSurgeMultiplier(), t.getFareEstimate());
    }
}
