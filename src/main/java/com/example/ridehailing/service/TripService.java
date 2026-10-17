package com.example.ridehailing.service;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripRepository;
import com.example.ridehailing.domain.TripStatus;
import com.example.ridehailing.dto.NearbyDriver;
import com.example.ridehailing.dto.RideRequest;
import com.example.ridehailing.dto.TripEvent;
import com.example.ridehailing.metrics.AppMetrics;
import com.example.ridehailing.redis.DriverGeoIndex;
import io.micrometer.core.instrument.Timer;
import com.example.ridehailing.redis.DriverTripRegistry;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {
    private final TripRepository trips;
    private final DriverGeoIndex geoIndex;
    private final DriverTripRegistry registry;
    private final DispatchEventPublisher events;
    private final AppProperties props;
    private final AppMetrics metrics;
    private final SurgeService surge;

    public TripService(TripRepository trips, DriverGeoIndex geoIndex, DriverTripRegistry registry,
                       DispatchEventPublisher events, AppProperties props, AppMetrics metrics,
                       SurgeService surge) {
        this.surge = surge;
        this.metrics = metrics;
        this.trips = trips;
        this.geoIndex = geoIndex;
        this.registry = registry;
        this.events = events;
        this.props = props;
    }

    @Transactional
    public Trip requestRide(RideRequest req) {
        Trip trip = trips.save(new Trip(req.riderId(), req.pickupLat(), req.pickupLon(), req.dropoffLat(), req.dropoffLon()));
        SurgeService.Quote quote = surge.recordDemandAndQuote(req.pickupLat(), req.pickupLon());
        double km = GeoUtils.haversineMeters(req.pickupLat(), req.pickupLon(), req.dropoffLat(), req.dropoffLon());
        trip.applyPricing(quote.multiplier(), SurgePricing.fare(km, props.surge().baseFare(), props.surge().perKm(), quote.multiplier()));
        trips.save(trip);
        emit("TRIP_REQUESTED", trip);
        Timer.Sample sample = metrics.startMatch();
        List<NearbyDriver> candidates = geoIndex.findNearest(req.pickupLat(), req.pickupLon(),
                props.matching().radiusKm(), props.matching().maxCandidates());
        for (NearbyDriver c : candidates) {
            if (geoIndex.claim(c.driverId())) {
                trip.assignDriver(c.driverId());
                registry.assign(c.driverId(), trip.getId().toString());
                trips.save(trip);
                metrics.recordMatch(sample, true);
                emit("DRIVER_ASSIGNED", trip);
                return trip;
            }
        }
        metrics.recordMatch(sample, false);
        trip.transitionTo(TripStatus.NO_DRIVER_AVAILABLE);
        trips.save(trip);
        emit("NO_DRIVER_AVAILABLE", trip);
        return trip;
    }

    @Transactional
    public Trip updateStatus(UUID id, TripStatus next) {
        Trip trip = get(id);
        trip.transitionTo(next);
        trips.save(trip);
        if (next.isTerminal() && trip.getDriverId() != null) {
            registry.release(trip.getDriverId());
        }
        emit("STATUS_CHANGED", trip);
        return trip;
    }

    @Transactional(readOnly = true)
    public Trip get(UUID id) {
        return trips.findById(id).orElseThrow(() -> new NoSuchElementException("Trip not found: " + id));
    }

    private void emit(String type, Trip t) {
        events.publish(new TripEvent(type, t.getId().toString(), t.getDriverId(), t.getStatus(), System.currentTimeMillis()));
    }
}
