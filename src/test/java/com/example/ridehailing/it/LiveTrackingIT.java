package com.example.ridehailing.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.example.ridehailing.dto.TripView;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.graphql.client.WebSocketGraphQlClientInterceptor;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Live tracking over the raw WebSocket endpoint and over GraphQL subscriptions, both JWT-protected. */
class LiveTrackingIT extends AbstractIntegrationIT {

    TripView tripHolder;

    TripView matchedTrip(String driver, String rider) {
        double lat = 51.5074, lon = -0.1278;
        as(driver, "DRIVER");
        rest.postForEntity("/api/drivers/" + driver + "/location", Map.of("lat", lat, "lon", lon, "heading", 0, "speedKmh", 0), Void.class);
        as(rider, "RIDER");
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            TripView t = rest.postForObject("/api/rides", Map.of("riderId", rider, "pickupLat", lat, "pickupLon", lon,
                    "dropoffLat", 51.52, "dropoffLon", -0.1), TripView.class);
            if (t.driverId() != null) { tripHolder = t; return true; }
            return false;
        });
        return tripHolder;
    }

    void pump(String driver, int n) throws InterruptedException {
        as(driver, "DRIVER");
        for (int i = 0; i < n; i++) {
            rest.postForEntity("/api/drivers/" + driver + "/location",
                    Map.of("lat", 51.5074 + i * 0.0005, "lon", -0.1278, "heading", 0, "speedKmh", 20), Void.class);
            Thread.sleep(200);
        }
    }

    @Test
    void rawWebSocketReceivesLocations() throws Exception {
        TripView trip = matchedTrip("ws-driver", "ws-rider");
        List<String> frames = new CopyOnWriteArrayList<>();
        WebSocketSession s = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override protected void handleTextMessage(WebSocketSession session, TextMessage m) { frames.add(m.getPayload()); }
        }, "ws://localhost:" + port + "/ws/trips/" + trip.id() + "?access_token=" + token("ws-rider", "RIDER")).get();
        Thread.sleep(500);
        for (int i = 0; i < 10 && frames.stream().noneMatch(f -> f.contains("\"location\"")); i++) pump("ws-driver", 3);
        assertThat(frames).anyMatch(f -> f.contains("\"kind\":\"location\"") && f.contains(trip.id()));
        s.close();
    }

    @Test
    void rawWebSocketRejectsMissingOrInvalidToken() {
        String base = "ws://localhost:" + port + "/ws/trips/00000000-0000-0000-0000-000000000000";
        var handler = new TextWebSocketHandler();
        assertThatThrownBy(() -> new StandardWebSocketClient().execute(handler, base).get()).hasStackTraceContaining("401");
        assertThatThrownBy(() -> new StandardWebSocketClient().execute(handler, base + "?access_token=garbage").get())
                .hasStackTraceContaining("401");
    }

    private WebSocketGraphQlClient graphQl(String bearerToken) {
        var b = WebSocketGraphQlClient.builder(URI.create("ws://localhost:" + port + "/graphql"), new ReactorNettyWebSocketClient());
        if (bearerToken != null) {
            b.interceptor(new WebSocketGraphQlClientInterceptor() {
                @Override public Mono<Object> connectionInitPayload() {
                    return Mono.just(Map.of("Authorization", "Bearer " + bearerToken));
                }
            });
        }
        return b.build();
    }

    @Test
    void graphQlSubscriptionReceivesLocations() throws Exception {
        TripView trip = matchedTrip("gql-driver", "gql-rider");
        WebSocketGraphQlClient client = graphQl(token("gql-rider", "RIDER"));
        List<Double> lats = new CopyOnWriteArrayList<>();
        var sub = client.document("subscription { tripLocation(tripId: \"" + trip.id() + "\") { driverId lat lon } }")
                .retrieveSubscription("tripLocation").toEntity(Map.class).subscribe(m -> lats.add(((Number) m.get("lat")).doubleValue()));
        Thread.sleep(500);
        for (int i = 0; i < 10 && lats.isEmpty(); i++) pump("gql-driver", 3);
        sub.dispose();
        client.stop().block();
        assertThat(lats).isNotEmpty();
    }

    @Test
    void graphQlSubscriptionWithoutTokenGetsNoData() throws Exception {
        TripView trip = matchedTrip("gql-driver-2", "gql-rider-2");
        WebSocketGraphQlClient client = graphQl(null);
        List<Double> lats = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        var sub = client.document("subscription { tripLocation(tripId: \"" + trip.id() + "\") { lat } }")
                .retrieveSubscription("tripLocation").toEntity(Map.class)
                .subscribe(m -> lats.add(((Number) m.get("lat")).doubleValue()), errors::add);
        Thread.sleep(500);
        pump("gql-driver-2", 6);
        sub.dispose();
        client.stop().block();
        assertThat(lats).isEmpty();
    }
}
