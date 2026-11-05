package com.example.ridehailing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SurgePricingTest {
    @Test
    void noSurgeWhenSupplyCoversDemand() {
        assertThat(SurgePricing.multiplier(3, 5, 0.5, 3.0)).isEqualTo(1.0);
        assertThat(SurgePricing.multiplier(5, 5, 0.5, 3.0)).isEqualTo(1.0);
        assertThat(SurgePricing.multiplier(0, 0, 0.5, 3.0)).isEqualTo(1.0);
    }

    @Test
    void growsWithRatioAndIsCapped() {
        assertThat(SurgePricing.multiplier(6, 3, 0.5, 3.0)).isEqualTo(1.5);
        assertThat(SurgePricing.multiplier(10, 0, 0.5, 3.0)).isEqualTo(3.0); // zero supply floored to 1, capped
        assertThat(SurgePricing.multiplier(1000, 1, 0.5, 2.0)).isEqualTo(2.0);
    }

    @Test
    void cellIdGroupsNearbyPointsAndSeparatesFarOnes() {
        assertThat(SurgePricing.cellId(37.7701, -122.4101)).isEqualTo(SurgePricing.cellId(37.7749, -122.4194));
        assertThat(SurgePricing.cellId(37.7701, -122.4101)).isNotEqualTo(SurgePricing.cellId(37.80, -122.30));
    }

    @Test
    void cellCenterLiesInsideItsCell() {
        double[] c = SurgePricing.cellCenter(37.7749, -122.4194);
        assertThat(SurgePricing.cellId(c[0], c[1])).isEqualTo(SurgePricing.cellId(37.7749, -122.4194));
        assertThat(Math.abs(c[0] - 37.7749)).isLessThanOrEqualTo(SurgePricing.CELL_DEG);
    }

    @Test
    void fareScalesWithDistanceAndMultiplier() {
        assertThat(SurgePricing.fare(0, 2.5, 1.2, 1.0)).isEqualTo(2.5);
        assertThat(SurgePricing.fare(10_000, 2.5, 1.2, 2.0)).isEqualTo(29.0);
    }
}
