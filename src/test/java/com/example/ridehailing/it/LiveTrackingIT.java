package com.example.ridehailing.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ridehailing.dto.TripView;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import static org.awaitility.Awaitility.await;

/** Live tracking over the raw WebSocket endpoint and over GraphQL subscriptions. */
class LiveTrackingIT extends AbstractIntegrationIT {
    @Autowired TestRestTemplate rest;

    TripView matchedTrip(String driver) {
        double lat = 51.5074, lon = -0.1278;
        rest.postForEntity("/api/drivers/" + driver + "/location", Map.of("lat", lat, "lon", lon, "heading", 0, "speedKmh", 0), Void.class);
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            TripView t = rest.postForObject("/api/rides", Map.of("riderId", "r", "pickupLat", lat, "pickupLon", lon,
                    "dropoffLat", 51.52, "dropoffLon", -0.1), TripView.class);
            if (t.driverId() != null) { tripHolder = t; return true; }
            return false;
        });
        return tripHolder;
    }

    TripView tripHolder;

    void pump(String driver, int n) throws InterruptedException {
        for (int i = 0; i < n; i++) {
            rest.postForEntity("/api/drivers/" + driver + "/location",
                    Map.of("lat", 51.5074 + i * 0.0005, "lon", -0.1278, "heading", 0, "speedKmh", 20), Void.class);
            Thread.sleep(200);
        }
    }

    @Test
    void rawWebSocketReceivesLocations() throws Exception {
        TripView trip = matchedTrip("ws-driver");
        List<String> frames = new CopyOnWriteArrayList<>();
        WebSocketSession s = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override protected void handleTextMessage(WebSocketSession session, TextMessage m) { frames.add(m.getPayload()); }
        }, "ws://localhost:" + port + "/ws/trips/" + trip.id()).get();
        Thread.sleep(500);
        for (int i = 0; i < 10 && frames.stream().noneMatch(f -> f.contains("\"location\"")); i++) pump("ws-driver", 3);
        assertThat(frames).anyMatch(f -> f.contains("\"kind\":\"location\"") && f.contains(trip.id()));
        s.close();
    }

    @Test
    void graphQlSubscriptionReceivesLocations() throws Exception {
        TripView trip = matchedTrip("gql-driver");
        WebSocketGraphQlClient client = WebSocketGraphQlClient
                .builder(URI.create("ws://localhost:" + port + "/graphql"), new ReactorNettyWebSocketClient()).build();
        List<Double> lats = new CopyOnWriteArrayList<>();
        var sub = client.document("subscription { tripLocation(tripId: \"" + trip.id() + "\") { driverId lat lon } }")
                .retrieveSubscription("tripLocation").toEntity(Map.class).subscribe(m -> lats.add(((Number) m.get("lat")).doubleValue()));
        Thread.sleep(500);
        for (int i = 0; i < 10 && lats.isEmpty(); i++) pump("gql-driver", 3);
        sub.dispose();
        client.stop().block();
        assertThat(lats).isNotEmpty();
    }
}
