package com.example.ridehailing.domain;

import java.util.EnumSet;
import java.util.Set;

public enum TripStatus {
    REQUESTED, DRIVER_ASSIGNED, DRIVER_ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED, NO_DRIVER_AVAILABLE;

    public Set<TripStatus> allowedNext() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(DRIVER_ASSIGNED, NO_DRIVER_AVAILABLE, CANCELLED);
            case DRIVER_ASSIGNED -> EnumSet.of(DRIVER_ARRIVED, CANCELLED);
            case DRIVER_ARRIVED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELLED, NO_DRIVER_AVAILABLE -> EnumSet.noneOf(TripStatus.class);
        };
    }

    public boolean canTransitionTo(TripStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    public static Set<TripStatus> active() {
        return EnumSet.of(DRIVER_ASSIGNED, DRIVER_ARRIVED, IN_PROGRESS);
    }
}
