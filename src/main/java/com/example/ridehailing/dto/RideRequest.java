package com.example.ridehailing.dto;

import jakarta.validation.constraints.*;

public record RideRequest(@NotBlank String riderId,
                          @DecimalMin("-90") @DecimalMax("90") double pickupLat,
                          @DecimalMin("-180") @DecimalMax("180") double pickupLon,
                          @DecimalMin("-90") @DecimalMax("90") double dropoffLat,
                          @DecimalMin("-180") @DecimalMax("180") double dropoffLon) {}
