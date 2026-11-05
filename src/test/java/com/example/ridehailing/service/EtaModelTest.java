package com.example.ridehailing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EtaModelTest {
    @Test
    void distanceOverSpeedWithRoadFactor() {
        // 3.6 km/h = 1 m/s; 1000 m straight * 1.0 factor => 1000 s... use 36 km/h = 10 m/s
        assertThat(EtaModel.etaSeconds(1000, 36, 5, 1.0)).isEqualTo(100);
        assertThat(EtaModel.etaSeconds(1000, 36, 5, 1.5)).isEqualTo(150);
    }

    @Test
    void speedIsFlooredAndZeroDistanceIsZero() {
        assertThat(EtaModel.etaSeconds(1000, 0, 18, 1.0)).isEqualTo(200); // floored to 18 km/h = 5 m/s
        assertThat(EtaModel.etaSeconds(0, 30, 5, 1.3)).isZero();
    }

    @Test
    void emaSeedsThenSmooths() {
        assertThat(EtaModel.ema(null, 40, 0.2)).isEqualTo(40);
        assertThat(EtaModel.ema(40.0, 20, 0.5)).isEqualTo(30);
    }
}
