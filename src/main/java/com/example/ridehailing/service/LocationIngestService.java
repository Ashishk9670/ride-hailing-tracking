package com.example.ridehailing.service;

import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.redis.DriverGeoIndex;
import com.example.ridehailing.redis.DriverTripRegistry;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Core of the Kafka consumer: updates the Redis geo index or the trip tracking pipeline. */
@Service
public class LocationIngestService {
    private final DriverGeoIndex geoIndex;
    private final DriverTripRegistry registry;
    private final LocationHistoryRepository history;
    private final TripEventBroadcaster broadcaster;

    public LocationIngestService(DriverGeoIndex geoIndex, DriverTripRegistry registry,
                                 LocationHistoryRepository history, TripEventBroadcaster broadcaster) {
        this.geoIndex = geoIndex;
        this.registry = registry;
        this.history = history;
        this.broadcaster = broadcaster;
    }

    public void process(LocationUpdate update) {
        Optional<String> tripId = registry.activeTrip(update.driverId());
        if (tripId.isEmpty()) {
            geoIndex.upsert(update.driverId(), update.lat(), update.lon());
            return;
        }
        LocationUpdate tracked = update.withTripId(tripId.get());
        history.insert(tracked);
        broadcaster.publishLocation(tracked);
    }
}
