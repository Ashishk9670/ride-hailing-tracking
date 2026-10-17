package com.example.ridehailing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripRepository;
import com.example.ridehailing.domain.TripStatus;
import com.example.ridehailing.dto.NearbyDriver;
import com.example.ridehailing.dto.RideRequest;
import com.example.ridehailing.dto.TripEvent;
import com.example.ridehailing.metrics.AppMetrics;
import com.example.ridehailing.redis.DriverGeoIndex;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.example.ridehailing.redis.DriverTripRegistry;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TripServiceTest {
    TripRepository trips = mock(TripRepository.class);
    DriverGeoIndex geo = mock(DriverGeoIndex.class);
    DriverTripRegistry registry = mock(DriverTripRegistry.class);
    DispatchEventPublisher events = mock(DispatchEventPublisher.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    AppMetrics metrics = new AppMetrics(meters);
    SurgeService surge = mock(SurgeService.class);
    EtaService etaService = mock(EtaService.class);
    TripService service;
    final RideRequest req = new RideRequest("rider", 37.77, -122.41, 37.80, -122.40);

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(new AppProperties.Topics("t", 1),
                new AppProperties.Redis("geo", "chan", "seen", 30, 5000), new AppProperties.Matching(5, 3),
                new AppProperties.Retry(3, 10, 2.0, 100),
                new AppProperties.Surge("surge:", 300, 0.5, 3.0, 2.5, 1.2),
                new AppProperties.Eta(30, 5, 1.3, 0.2, "eta:", 900, 5));
        when(trips.save(any(Trip.class))).thenAnswer(i -> i.getArgument(0));
        when(surge.recordDemandAndQuote(anyDouble(), anyDouble())).thenReturn(new SurgeService.Quote("c", 4, 2, 1.5));
        service = new TripService(trips, geo, registry, events, props, metrics, surge, etaService);
    }

    @Test
    void assignsNearestClaimableDriver() {
        when(geo.findNearest(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(
                new NearbyDriver("d1", 100, 0, 0), new NearbyDriver("d2", 200, 0, 0)));
        when(geo.claim("d1")).thenReturn(false); // lost the race
        when(geo.claim("d2")).thenReturn(true);
        when(etaService.estimateSeconds(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(240);

        Trip t = service.requestRide(req);

        assertThat(t.getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
        assertThat(t.getDriverId()).isEqualTo("d2");
        verify(registry).assign("d2", t.getId().toString());
        assertThat(t.getEtaSeconds()).isEqualTo(240);
        assertThat(meters.get("rides.matches").tag("result", "matched").counter().count()).isEqualTo(1.0);
        assertThat(meters.get("rides.match.latency").tag("result", "matched").timer().count()).isEqualTo(1L);
        ArgumentCaptor<TripEvent> ev = ArgumentCaptor.forClass(TripEvent.class);
        verify(events, times(2)).publish(ev.capture());
        assertThat(ev.getAllValues()).extracting(TripEvent::type).containsExactly("TRIP_REQUESTED", "DRIVER_ASSIGNED");
        assertThat(ev.getAllValues().get(1).etaSeconds()).isEqualTo(240);
    }

    @Test
    void appliesSurgeMultiplierToFare() {
        when(geo.findNearest(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());
        Trip t = service.requestRide(req);
        double km = GeoUtils.haversineMeters(37.77, -122.41, 37.80, -122.40);
        assertThat(t.getSurgeMultiplier()).isEqualTo(1.5);
        assertThat(t.getFareEstimate()).isEqualTo(SurgePricing.fare(km, 2.5, 1.2, 1.5));
    }

    @Test
    void noDriversMarksTrip() {
        when(geo.findNearest(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());
        Trip t = service.requestRide(req);
        assertThat(t.getStatus()).isEqualTo(TripStatus.NO_DRIVER_AVAILABLE);
        verify(registry, never()).assign(any(), any());
        assertThat(meters.get("rides.matches").tag("result", "no_driver").counter().count()).isEqualTo(1.0);
    }

    @Test
    void completingReleasesDriver() {
        Trip t = new Trip("r", 1, 1, 2, 2);
        t.assignDriver("d1");
        t.transitionTo(TripStatus.DRIVER_ARRIVED);
        t.transitionTo(TripStatus.IN_PROGRESS);
        when(trips.findById(t.getId())).thenReturn(Optional.of(t));

        service.updateStatus(t.getId(), TripStatus.COMPLETED);

        verify(registry).release("d1");
        assertThat(t.getStatus()).isEqualTo(TripStatus.COMPLETED);
    }

    @Test
    void illegalTransitionThrows() {
        Trip t = new Trip("r", 1, 1, 2, 2);
        when(trips.findById(t.getId())).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.updateStatus(t.getId(), TripStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
        verify(registry, never()).release(any());
    }

    @Test
    void missingTripThrows() {
        when(trips.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);
    }
}
