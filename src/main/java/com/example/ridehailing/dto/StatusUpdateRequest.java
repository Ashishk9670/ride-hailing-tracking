package com.example.ridehailing.dto;

import com.example.ridehailing.domain.TripStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull TripStatus status) {}
