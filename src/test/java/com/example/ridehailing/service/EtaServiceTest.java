package com.example.ridehailing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.redis.CellSpeedTracker;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class EtaServiceTest {
    LocationHistoryRepository repo = mock(LocationHistoryRepository.class);
    CellSpeedTracker speeds = mock(CellSpeedTracker.class);
    AppProperties props = new AppProperties(null, null, null, null, null,
            new AppProperties.Eta(36, 5, 1.0, 0.2, "e:", 900, 5));
    EtaService svc = new EtaService(repo, speeds, props);

    @Test
    void usesPostgisDistanceAndCellSpeed() {
        when(repo.distanceMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(1000.0);
        when(speeds.averageKmh(anyDouble(), anyDouble())).thenReturn(OptionalDouble.of(72));
        assertThat(svc.estimateSeconds(1, 1, 2, 2)).isEqualTo(50);
    }

    @Test
    void fallsBackToDefaultSpeedWhenNoSamples() {
        when(repo.distanceMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(1000.0);
        when(speeds.averageKmh(anyDouble(), anyDouble())).thenReturn(OptionalDouble.empty());
        assertThat(svc.estimateSeconds(1, 1, 2, 2)).isEqualTo(100);
    }

    @Test
    void fallsBackToHaversineWhenPostgisFails() {
        when(repo.distanceMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenThrow(new RuntimeException("db"));
        when(speeds.averageKmh(anyDouble(), anyDouble())).thenReturn(OptionalDouble.empty());
        assertThat(svc.estimateSeconds(0, 0, 0, 0.01)).isGreaterThan(0);
    }
}
