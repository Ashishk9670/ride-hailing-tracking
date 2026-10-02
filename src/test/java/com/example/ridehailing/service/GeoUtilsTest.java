package com.example.ridehailing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {
    @Test
    void haversineKnownDistance() {
        // ~111.2 km per degree of latitude
        assertThat(GeoUtils.haversineMeters(0, 0, 1, 0)).isCloseTo(111_195, within(200.0));
        assertThat(GeoUtils.haversineMeters(10, 10, 10, 10)).isZero();
    }

    @Test
    void bearingCardinalDirections() {
        assertThat(GeoUtils.bearing(0, 0, 1, 0)).isCloseTo(0, within(0.5));
        assertThat(GeoUtils.bearing(0, 0, 0, 1)).isCloseTo(90, within(0.5));
        assertThat(GeoUtils.bearing(0, 0, -1, 0)).isCloseTo(180, within(0.5));
        assertThat(GeoUtils.bearing(0, 0, 0, -1)).isCloseTo(270, within(0.5));
    }

    @Test
    void moveTowardStepsAndSnaps() {
        double[] p = GeoUtils.moveToward(0, 0, 0, 0.01, 100);
        assertThat(GeoUtils.haversineMeters(0, 0, p[0], p[1])).isCloseTo(100, within(2.0));
        double[] snap = GeoUtils.moveToward(0, 0, 0, 0.0001, 100);
        assertThat(snap).containsExactly(0, 0.0001);
    }
}
