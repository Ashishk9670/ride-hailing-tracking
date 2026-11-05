package com.example.ridehailing.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripRepository;
import com.example.ridehailing.domain.TripStatus;
import com.example.ridehailing.dto.NearbyDriver;
import com.example.ridehailing.dto.RideRequest;
import com.example.ridehailing.metrics.AppMetrics;
import com.example.ridehailing.redis.DriverGeoIndex;
import com.example.ridehailing.redis.DriverTripRegistry;
import com.example.ridehailing.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.Size;

/** Property-based tests for geodesy helpers and the nearest-claimable-driver matching rule. */
class GeoPropertiesTest {
    @Provide Arbitrary<Double> lats() { return Arbitraries.doubles().between(-85, 85); }
    @Provide Arbitrary<Double> lons() { return Arbitraries.doubles().between(-179, 179); }

    @Property
    void distanceIsSymmetricNonNegativeAndZeroToSelf(@ForAll("lats") double a, @ForAll("lons") double b,
                                                     @ForAll("lats") double c, @ForAll("lons") double d) {
        double ab = GeoUtils.haversineMeters(a, b, c, d);
        assertThat(ab).isGreaterThanOrEqualTo(0);
        assertThat(ab).isCloseTo(GeoUtils.haversineMeters(c, d, a, b), org.assertj.core.data.Offset.offset(1e-6));
        assertThat(GeoUtils.haversineMeters(a, b, a, b)).isZero();
        assertThat(ab).isLessThanOrEqualTo(Math.PI * 6_371_000.0 + 1);
    }

    @Property
    void triangleInequalityHolds(@ForAll("lats") double a1, @ForAll("lons") double o1,
                                 @ForAll("lats") double a2, @ForAll("lons") double o2,
                                 @ForAll("lats") double a3, @ForAll("lons") double o3) {
        double direct = GeoUtils.haversineMeters(a1, o1, a3, o3);
        double via = GeoUtils.haversineMeters(a1, o1, a2, o2) + GeoUtils.haversineMeters(a2, o2, a3, o3);
        assertThat(direct).isLessThanOrEqualTo(via + 1e-3);
    }

    @Property
    void bearingIsWithinCompassRange(@ForAll("lats") double a, @ForAll("lons") double b,
                                     @ForAll("lats") double c, @ForAll("lons") double d) {
        assertThat(GeoUtils.bearing(a, b, c, d)).isBetween(0.0, 360.0);
    }

    @Property
    void moveTowardNeverOvershootsAndNeverMovesAway(@ForAll("lats") double a, @ForAll("lons") double b,
                                                    @ForAll @DoubleRange(min = -0.5, max = 0.5) double dLat,
                                                    @ForAll @DoubleRange(min = -0.5, max = 0.5) double dLon,
                                                    @ForAll @DoubleRange(min = 1, max = 5000) double step) {
        // city-scale targets only: moveToward interpolates linearly in lat/lon, which is not a great-circle path
        // and can drift away from the target over continental distances (documented limitation).
        double c = a + dLat, d = b + dLon;
        double before = GeoUtils.haversineMeters(a, b, c, d);
        double[] p = GeoUtils.moveToward(a, b, c, d, step);
        double after = GeoUtils.haversineMeters(p[0], p[1], c, d);
        assertThat(after).isLessThanOrEqualTo(before + 1e-6);
        if (before <= step) assertThat(after).isZero();
    }

    /** The assigned driver is always the first candidate (nearest-first order) whose claim succeeds. */
    @Property(tries = 100)
    void assignsFirstClaimableCandidate(@ForAll @Size(max = 8) List<Boolean> claimable) {
        TripRepository trips = mock(TripRepository.class);
        DriverGeoIndex geo = mock(DriverGeoIndex.class);
        DriverTripRegistry registry = mock(DriverTripRegistry.class);
        SurgeService surge = mock(SurgeService.class);
        EtaService eta = mock(EtaService.class);
        when(trips.save(any(Trip.class))).thenAnswer(i -> i.getArgument(0));
        when(surge.recordDemandAndQuote(anyDouble(), anyDouble())).thenReturn(new SurgeService.Quote("c", 0, 0, 1.0));
        when(eta.estimateSeconds(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(60);
        List<NearbyDriver> cands = new ArrayList<>();
        for (int i = 0; i < claimable.size(); i++) {
            cands.add(new NearbyDriver("d" + i, i * 100.0, 0, 0));
            when(geo.claim("d" + i)).thenReturn(claimable.get(i));
        }
        when(geo.findNearest(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(cands);
        AppProperties props = new AppProperties(new AppProperties.Topics("t", 1),
                new AppProperties.Redis("g", "c", "s", 30, 5000), new AppProperties.Matching(5, 8),
                new AppProperties.Retry(3, 10, 2.0, 100), new AppProperties.Surge("s:", 300, 0.5, 3.0, 2.5, 1.2),
                new AppProperties.Eta(30, 5, 1.3, 0.2, "e:", 900, 5));
        TripService svc = new TripService(trips, geo, registry, mock(DispatchEventPublisher.class), props,
                new AppMetrics(new SimpleMeterRegistry()), surge, eta);

        Trip t = svc.requestRide(new RideRequest("r", 1, 1, 2, 2));

        int first = claimable.indexOf(true);
        if (first < 0) {
            assertThat(t.getStatus()).isEqualTo(TripStatus.NO_DRIVER_AVAILABLE);
            assertThat(t.getDriverId()).isNull();
        } else {
            assertThat(t.getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
            assertThat(t.getDriverId()).isEqualTo("d" + first);
            for (int i = first + 1; i < claimable.size(); i++) verify(geo, never()).claim("d" + i);
        }
    }
}
