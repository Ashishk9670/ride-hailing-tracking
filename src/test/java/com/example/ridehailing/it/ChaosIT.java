package com.example.ridehailing.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripRepository;
import com.example.ridehailing.domain.TripStatus;
import com.example.ridehailing.dto.NearbyDriver;
import com.example.ridehailing.security.JwtTokenService;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chaos: Redis is reached through Toxiproxy and cut mid-run; Kafka is frozen with {@code docker pause} (its advertised
 * listener would bypass a proxy). Asserts graceful degradation (503, no 500s, no half-created trips), no driver is ever
 * assigned to two active trips, and that the service recovers once the dependency is back.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChaosIT {
    static final double LAT = 51.5072, LON = -0.1276;
    static final Network NET = Network.newNetwork();
    static GenericContainer<?> redis;
    static ToxiproxyContainer toxiproxy;
    static Proxy redisProxy;

    @Autowired TestRestTemplate rest;
    @Autowired JwtTokenService tokens;
    @Autowired TripRepository trips;

    @BeforeAll
    static synchronized void infra() throws Exception {
        if (redisProxy != null) return;
        AbstractIntegrationIT.startAll();
        redis = new GenericContainer<>("redis:7-alpine").withNetwork(NET).withNetworkAliases("redis").withExposedPorts(6379);
        toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0").withNetwork(NET);
        redis.start();
        toxiproxy.start();
        redisProxy = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort())
                .createProxy("redis", "0.0.0.0:8666", "redis:6379");
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        infra();
        r.add("spring.datasource.url", AbstractIntegrationIT.postgres::getJdbcUrl);
        r.add("spring.datasource.username", AbstractIntegrationIT.postgres::getUsername);
        r.add("spring.datasource.password", AbstractIntegrationIT.postgres::getPassword);
        r.add("spring.data.redis.host", toxiproxy::getHost);
        r.add("spring.data.redis.port", () -> toxiproxy.getMappedPort(8666));
        r.add("spring.data.redis.timeout", () -> "1s");
        r.add("spring.kafka.bootstrap-servers", AbstractIntegrationIT.kafka::getBootstrapServers);
        r.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        // isolate from the other ITs' contexts that share this broker
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        r.add("ridehailing.topics.driver-locations", () -> "chaos-locations-" + suffix);
        r.add("spring.kafka.consumer.group-id", () -> "chaos-" + suffix);
    }

    @AfterEach
    void heal() throws Exception {
        redisProxy.enable();
        try {
            AbstractIntegrationIT.kafka.getDockerClient().unpauseContainerCmd(AbstractIntegrationIT.kafka.getContainerId()).exec();
        } catch (Exception ignored) {
            // not paused
        }
    }

    private HttpHeaders auth(String subject, String role) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tokens.issue(subject, List.of(role)));
        return h;
    }

    private ResponseEntity<String> postLocation(String driver, double lat, double lon) {
        return rest.exchange("/api/drivers/" + driver + "/location", HttpMethod.POST,
                new HttpEntity<>(Map.of("lat", lat, "lon", lon, "heading", 0, "speedKmh", 10), auth(driver, "DRIVER")),
                String.class);
    }

    private ResponseEntity<String> requestRide(String rider) {
        return rest.exchange("/api/rides", HttpMethod.POST,
                new HttpEntity<>(Map.of("riderId", rider, "pickupLat", LAT + 0.0001, "pickupLon", LON + 0.0001,
                        "dropoffLat", LAT + 0.02, "dropoffLon", LON + 0.02), auth(rider, "RIDER")), String.class);
    }

    private List<NearbyDriver> nearby() {
        return rest.exchange("/api/drivers/nearby?lat={a}&lon={b}&radiusKm=10&limit=50", HttpMethod.GET,
                new HttpEntity<>(auth("rider-x", "RIDER")), new ParameterizedTypeReference<List<NearbyDriver>>() {},
                LAT, LON).getBody();
    }

    private void seedDrivers(String prefix, int n) {
        for (int i = 0; i < n; i++) {
            assertThat(postLocation(prefix + i, LAT + i * 0.0005, LON).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(nearby().stream().filter(d -> d.driverId().startsWith(prefix)).count()).isEqualTo(n));
    }

    private void assertNoDoubleAssignment() {
        Map<String, Long> perDriver = trips.findAll().stream()
                .filter(t -> TripStatus.active().contains(t.getStatus()))
                .collect(Collectors.groupingBy(Trip::getDriverId, Collectors.counting()));
        assertThat(perDriver.values()).allMatch(c -> c == 1L);
    }

    @Test
    @Order(1)
    void redisCutFailsMatchingCleanlyThenRecovers() throws Exception {
        seedDrivers("cut-", 3);
        long tripsBefore = trips.count();

        redisProxy.disable();
        ResponseEntity<String> ride = requestRide("rider-cut");
        assertThat(ride.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ride.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(rest.exchange("/api/drivers/nearby?lat=1&lon=1", HttpMethod.GET,
                new HttpEntity<>(auth("rider-cut", "RIDER")), String.class).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // the failed request must not leave a trip behind (transaction rolled back)
        assertThat(trips.count()).isEqualTo(tripsBefore);

        redisProxy.enable();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(requestRide("rider-cut").getStatusCode()).isEqualTo(HttpStatus.CREATED));
        assertNoDoubleAssignment();
    }

    @Test
    @Order(2)
    void redisFlapDuringConcurrentMatchingNeverDoubleAssigns() throws Exception {
        seedDrivers("flap-", 4);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Future<ResponseEntity<String>>> results = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String rider = "flap-rider-" + i;
            results.add(pool.submit(() -> {
                Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(1500));
                return requestRide(rider);
            }));
        }
        Thread.sleep(300);
        redisProxy.disable();
        Thread.sleep(1200);
        redisProxy.enable();

        for (Future<ResponseEntity<String>> f : results) {
            HttpStatus s = HttpStatus.valueOf(f.get().getStatusCode().value());
            assertThat(s).isIn(HttpStatus.CREATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
        pool.shutdown();
        assertNoDoubleAssignment();
        // trips that did get created were either matched or cleanly marked unmatched, never left half-built
        assertThat(trips.findAll()).filteredOn(t -> t.getRiderId().startsWith("flap-rider-"))
                .allMatch(t -> t.getStatus() != TripStatus.REQUESTED);

        // recovery: claimed-but-unassigned drivers come back with their next location update
        for (int i = 0; i < 4; i++) postLocation("flap-" + i, LAT + i * 0.0005, LON);
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(nearby().stream().filter(d -> d.driverId().startsWith("flap-")).count())
                        .isGreaterThanOrEqualTo(1));
    }

    @Test
    @Order(3)
    void kafkaCutDegradesIngestAndRecovers() {
        seedDrivers("kafka-", 1);
        var docker = AbstractIntegrationIT.kafka.getDockerClient();
        docker.pauseContainerCmd(AbstractIntegrationIT.kafka.getContainerId()).exec();

        long start = System.nanoTime();
        ResponseEntity<String> r = postLocation("kafka-0", LAT, LON);
        // either rejected cleanly (503) or accepted into the producer buffer (202); never a 500, never a hang
        assertThat(r.getStatusCode().value()).isIn(202, 503);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));

        // the rest of the API keeps working without Kafka (matching only needs Redis + Postgres)
        assertThat(requestRide("rider-kafka").getStatusCode().value()).isIn(201);

        docker.unpauseContainerCmd(AbstractIntegrationIT.kafka.getContainerId()).exec();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(postLocation("kafka-recovered", LAT, LON).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(nearby()).extracting(NearbyDriver::driverId).contains("kafka-recovered");
        });
        assertNoDoubleAssignment();
    }
}
