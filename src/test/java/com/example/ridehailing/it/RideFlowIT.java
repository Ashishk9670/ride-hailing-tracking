package com.example.ridehailing.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.ridehailing.dto.NearbyDriver;
import com.example.ridehailing.dto.TripView;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** REST + Kafka + Redis GEO + PostGIS end to end (geo matching, history, status machine). */
class RideFlowIT extends AbstractIntegrationIT {
    void driverAt(String id, double lat, double lon) {
        as(id, "DRIVER");
        var r = rest.postForEntity("/api/drivers/" + id + "/location",
                Map.of("lat", lat, "lon", lon, "heading", 0, "speedKmh", 10), Void.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    List<NearbyDriver> nearby(double lat, double lon, double km) {
        as("rider-1", "RIDER");
        return rest.exchange("/api/drivers/nearby?lat={a}&lon={b}&radiusKm={c}", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<NearbyDriver>>() {}, lat, lon, km).getBody();
    }

    @Test
    void nearestDriverMatchedAndTracked() {
        driverAt("it-near", 40.7128, -74.0060);
        driverAt("it-far", 40.7500, -74.0060); // ~4 km away
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(nearby(40.7128, -74.0060, 10)).extracting(NearbyDriver::driverId).contains("it-near", "it-far"));
        assertThat(nearby(40.7128, -74.0060, 10).get(0).driverId()).isEqualTo("it-near");
        assertThat(nearby(40.7128, -74.0060, 1)).extracting(NearbyDriver::driverId).containsExactly("it-near");

        as("rider-1", "RIDER");
        TripView trip = rest.postForObject("/api/rides",
                Map.of("riderId", "rider-1", "pickupLat", 40.7129, "pickupLon", -74.0061,
                        "dropoffLat", 40.73, "dropoffLon", -73.99), TripView.class);
        assertThat(trip.status().name()).isEqualTo("DRIVER_ASSIGNED");
        assertThat(trip.surgeMultiplier()).isGreaterThanOrEqualTo(1.0);
        assertThat(trip.fareEstimate()).isGreaterThan(0);
        assertThat(trip.driverId()).isEqualTo("it-near");

        // matched driver is no longer available
        assertThat(nearby(40.7128, -74.0060, 1)).isEmpty();

        // illegal transition -> 409
        var bad = rest.exchange("/api/trips/{id}/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "COMPLETED")), String.class, trip.id());
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        as("it-near", "DRIVER");
        // location updates now persisted in PostGIS history
        for (int i = 0; i < 3; i++) driverAt("it-near", 40.7128 + i * 0.001, -74.0060);
        as("rider-1", "RIDER");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<?, ?> h = rest.getForObject("/api/trips/{id}/history", Map.class, trip.id());
            assertThat((List<?>) h.get("points")).hasSize(3);
            assertThat(((Number) h.get("routeLengthMeters")).doubleValue()).isBetween(150.0, 300.0);
        });

        for (String s : List.of("DRIVER_ARRIVED", "IN_PROGRESS", "COMPLETED")) {
            var ok = rest.exchange("/api/trips/{id}/status", HttpMethod.PATCH,
                    new HttpEntity<>(Map.of("status", s)), TripView.class, trip.id());
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void noDriverInRange() {
        as("r", "RIDER");
        TripView trip = rest.postForObject("/api/rides",
                Map.of("riderId", "r", "pickupLat", -33.0, "pickupLon", 151.0, "dropoffLat", -33.1, "dropoffLon", 151.1),
                TripView.class);
        assertThat(trip.status().name()).isEqualTo("NO_DRIVER_AVAILABLE");
    }

    @Test
    void securityIsEnforcedOnRealStack() {
        bearer = null;
        assertThat(rest.postForEntity("/api/rides", Map.of("riderId", "x", "pickupLat", 1, "pickupLon", 1,
                "dropoffLat", 1, "dropoffLon", 1), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/drivers/nearby?lat=1&lon=1", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // a rider cannot report driver locations, a driver cannot spoof another driver
        as("rider-1", "RIDER");
        assertThat(rest.postForEntity("/api/drivers/d9/location", Map.of("lat", 1, "lon", 1, "heading", 0, "speedKmh", 0),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        as("other-driver", "DRIVER");
        assertThat(rest.postForEntity("/api/drivers/d9/location", Map.of("lat", 1, "lon", 1, "heading", 0, "speedKmh", 0),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // health and metrics stay open for probes/scrapers
        bearer = null;
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        String prom = rest.getForObject("/actuator/prometheus", String.class);
        assertThat(prom).contains("rides_matches_total", "driver_location_updates_total", "location_dlt_total");
    }
}
