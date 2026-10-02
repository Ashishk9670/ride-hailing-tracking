package com.example.ridehailing.dto;

import jakarta.validation.constraints.*;

public record DriverLocationRequest(@DecimalMin("-90") @DecimalMax("90") double lat,
                                    @DecimalMin("-180") @DecimalMax("180") double lon,
                                    double heading, double speedKmh) {}
