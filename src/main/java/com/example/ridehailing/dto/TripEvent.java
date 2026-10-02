package com.example.ridehailing.dto;

import com.example.ridehailing.domain.TripStatus;

public record TripEvent(String type, String tripId, String driverId, TripStatus status, long timestamp) {}
