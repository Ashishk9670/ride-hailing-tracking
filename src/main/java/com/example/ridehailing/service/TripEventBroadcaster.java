package com.example.ridehailing.service;

import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.dto.TripEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/** In-process fan-out of live data to GraphQL subscriptions and raw WebSocket sessions. */
@Component
public class TripEventBroadcaster {
    private final Sinks.Many<LocationUpdate> locations = Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<TripEvent> events = Sinks.many().multicast().directBestEffort();

    public void publishLocation(LocationUpdate u) { locations.tryEmitNext(u); }

    public void publishEvent(TripEvent e) { events.tryEmitNext(e); }

    public Flux<LocationUpdate> locations(String tripId) {
        return locations.asFlux().filter(u -> tripId.equals(u.tripId()));
    }

    public Flux<TripEvent> events(String tripId) {
        return events.asFlux().filter(e -> tripId.equals(e.tripId()));
    }
}
