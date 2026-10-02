package com.example.ridehailing.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.redis.DriverGeoIndex;
import com.example.ridehailing.redis.DriverTripRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocationIngestServiceTest {
    DriverGeoIndex geo = mock(DriverGeoIndex.class);
    DriverTripRegistry registry = mock(DriverTripRegistry.class);
    LocationHistoryRepository history = mock(LocationHistoryRepository.class);
    TripEventBroadcaster broadcaster = mock(TripEventBroadcaster.class);
    io.micrometer.core.instrument.simple.SimpleMeterRegistry meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    com.example.ridehailing.metrics.AppMetrics metrics = new com.example.ridehailing.metrics.AppMetrics(meters);
    LocationIngestService svc = new LocationIngestService(geo, registry, history, broadcaster, metrics);
    LocationUpdate u = new LocationUpdate("d1", null, 10, 20, 90, 30, 1000L);

    @Test
    void idleDriverUpdatesGeoIndexOnly() {
        when(registry.activeTrip("d1")).thenReturn(Optional.empty());
        svc.process(u);
        verify(geo).upsert("d1", 10, 20);
        verifyNoInteractions(history, broadcaster);
    }

    @Test
    void busyDriverIsTrackedAndBroadcast() {
        when(registry.activeTrip("d1")).thenReturn(Optional.of("trip-1"));
        svc.process(u);
        ArgumentCaptor<LocationUpdate> c = ArgumentCaptor.forClass(LocationUpdate.class);
        verify(history).insert(c.capture());
        verify(broadcaster).publishLocation(c.getValue());
        verify(geo, never()).upsert(any(), anyDouble(), anyDouble());
        org.assertj.core.api.Assertions.assertThat(c.getValue().tripId()).isEqualTo("trip-1");
    }

    @Test
    void invalidCoordinatesAreRejectedForDeadLettering() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> svc.process(new LocationUpdate("d1", null, 999, 20, 0, 0, 1L)))
                .isInstanceOf(InvalidLocationException.class);
        verifyNoInteractions(geo, history, broadcaster);
    }

    @Test
    void processedUpdatesAreCounted() {
        when(registry.activeTrip("d1")).thenReturn(Optional.empty());
        svc.process(u);
        org.assertj.core.api.Assertions.assertThat(
                meters.get("driver.location.updates").tag("stage", "processed").counter().count()).isEqualTo(1.0);
    }
}
