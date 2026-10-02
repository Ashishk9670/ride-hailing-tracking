package com.example.ridehailing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TripStatusTest {
    @Test
    void happyPathIsAllowed() {
        assertThat(TripStatus.REQUESTED.canTransitionTo(TripStatus.DRIVER_ASSIGNED)).isTrue();
        assertThat(TripStatus.DRIVER_ASSIGNED.canTransitionTo(TripStatus.DRIVER_ARRIVED)).isTrue();
        assertThat(TripStatus.DRIVER_ARRIVED.canTransitionTo(TripStatus.IN_PROGRESS)).isTrue();
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.COMPLETED)).isTrue();
    }

    @Test
    void skippingStatesIsRejected() {
        assertThat(TripStatus.REQUESTED.canTransitionTo(TripStatus.IN_PROGRESS)).isFalse();
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.CANCELLED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TripStatus.class, names = {"COMPLETED", "CANCELLED", "NO_DRIVER_AVAILABLE"})
    void terminalStatesHaveNoTransitions(TripStatus s) {
        assertThat(s.isTerminal()).isTrue();
        assertThat(s.allowedNext()).isEmpty();
    }

    @Test
    void activeSetContainsDrivingStates() {
        assertThat(TripStatus.active()).containsExactlyInAnyOrder(
                TripStatus.DRIVER_ASSIGNED, TripStatus.DRIVER_ARRIVED, TripStatus.IN_PROGRESS);
    }

    @Test
    void tripEntityEnforcesTransitions() {
        Trip t = new Trip("r1", 1, 2, 3, 4);
        assertThat(t.getStatus()).isEqualTo(TripStatus.REQUESTED);
        t.assignDriver("d1");
        assertThat(t.getDriverId()).isEqualTo("d1");
        assertThatThrownBy(() -> t.transitionTo(TripStatus.COMPLETED)).isInstanceOf(IllegalStateException.class);
    }
}
