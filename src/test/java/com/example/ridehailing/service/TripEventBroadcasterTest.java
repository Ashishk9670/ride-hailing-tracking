package com.example.ridehailing.service;

import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.dto.TripEvent;
import com.example.ridehailing.domain.TripStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class TripEventBroadcasterTest {
    @Test
    void filtersLocationsByTrip() {
        TripEventBroadcaster b = new TripEventBroadcaster();
        StepVerifier.create(b.locations("t1").take(1))
                .then(() -> {
                    b.publishLocation(new LocationUpdate("d", "t2", 1, 1, 0, 0, 1));
                    b.publishLocation(new LocationUpdate("d", "t1", 2, 2, 0, 0, 2));
                })
                .expectNextMatches(u -> u.lat() == 2)
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void filtersEventsByTrip() {
        TripEventBroadcaster b = new TripEventBroadcaster();
        StepVerifier.create(b.events("t1").take(1))
                .then(() -> {
                    b.publishEvent(new TripEvent("X", "other", null, TripStatus.REQUESTED, 1));
                    b.publishEvent(new TripEvent("Y", "t1", "d", TripStatus.DRIVER_ASSIGNED, 2));
                })
                .expectNextMatches(e -> e.type().equals("Y"))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }
}
