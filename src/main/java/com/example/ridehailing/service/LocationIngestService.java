package com.example.ridehailing.service;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripRepository;
import com.example.ridehailing.domain.TripStatus;
import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.dto.TripEvent;
import com.example.ridehailing.redis.CellSpeedTracker;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.example.ridehailing.metrics.AppMetrics;
import com.example.ridehailing.redis.DriverGeoIndex;
import com.example.ridehailing.redis.DriverTripRegistry;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Core of the Kafka consumer: updates the Redis geo index or the trip tracking pipeline. */
@Service
public class LocationIngestService {
    private static final Logger log = LoggerFactory.getLogger(LocationIngestService.class);
    private final CellSpeedTracker speeds;
    private final TripRepository trips;
    private final EtaService eta;
    private final StringRedisTemplate redis;
    private final long etaThrottleSeconds;
    private final DriverGeoIndex geoIndex;
    private final DriverTripRegistry registry;
    private final LocationHistoryRepository history;
    private final TripEventBroadcaster broadcaster;

    private final AppMetrics metrics;

    public LocationIngestService(DriverGeoIndex geoIndex, DriverTripRegistry registry,
                                 LocationHistoryRepository history, TripEventBroadcaster broadcaster,
                                 AppMetrics metrics, CellSpeedTracker speeds, TripRepository trips,
                                 EtaService eta, StringRedisTemplate redis, AppProperties props) {
        this.speeds = speeds;
        this.trips = trips;
        this.eta = eta;
        this.redis = redis;
        this.etaThrottleSeconds = props.eta().updateThrottleSeconds();
        this.metrics = metrics;
        this.geoIndex = geoIndex;
        this.registry = registry;
        this.history = history;
        this.broadcaster = broadcaster;
    }

    public void process(LocationUpdate update) {
        if (update.driverId() == null || update.driverId().isBlank()
                || !(Math.abs(update.lat()) <= 90) || !(Math.abs(update.lon()) <= 180)) {
            throw new InvalidLocationException("Invalid location update: " + update);
        }
        metrics.locationProcessed();
        speeds.record(update.lat(), update.lon(), update.speedKmh());
        Optional<String> tripId = registry.activeTrip(update.driverId());
        if (tripId.isEmpty()) {
            geoIndex.upsert(update.driverId(), update.lat(), update.lon());
            return;
        }
        LocationUpdate tracked = update.withTripId(tripId.get());
        history.insert(tracked);
        broadcaster.publishLocation(tracked);
        publishEta(tracked);
    }

    /** Throttled live ETA to the current target (pickup while assigned, dropoff while in progress). Never fails ingest. */
    private void publishEta(LocationUpdate u) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent("eta:throttle:" + u.tripId(), "1",
                    Duration.ofSeconds(Math.max(etaThrottleSeconds, 1)));
            if (!Boolean.TRUE.equals(first)) return;
            Trip trip = trips.findById(UUID.fromString(u.tripId())).orElse(null);
            if (trip == null) return;
            double[] target;
            if (trip.getStatus() == TripStatus.DRIVER_ASSIGNED) target = new double[]{trip.getPickupLat(), trip.getPickupLon()};
            else if (trip.getStatus() == TripStatus.IN_PROGRESS) target = new double[]{trip.getDropoffLat(), trip.getDropoffLon()};
            else return;
            int seconds = eta.estimateSeconds(u.lat(), u.lon(), target[0], target[1]);
            broadcaster.publishEvent(new TripEvent("ETA_UPDATED", u.tripId(), u.driverId(), trip.getStatus(),
                    System.currentTimeMillis(), seconds));
        } catch (RuntimeException e) {
            log.warn("ETA update skipped for trip {}", u.tripId(), e);
        }
    }
}
