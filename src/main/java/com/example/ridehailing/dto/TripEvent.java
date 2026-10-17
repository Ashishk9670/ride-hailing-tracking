package com.example.ridehailing.dto;

import com.example.ridehailing.domain.TripStatus;

/** @param etaSeconds seconds until the driver reaches the current target (pickup or dropoff); null if unknown */
public record TripEvent(String type, String tripId, String driverId, TripStatus status, long timestamp,
                        Integer etaSeconds) {
    public TripEvent(String type, String tripId, String driverId, TripStatus status, long timestamp) {
        this(type, tripId, driverId, status, timestamp, null);
    }
}
