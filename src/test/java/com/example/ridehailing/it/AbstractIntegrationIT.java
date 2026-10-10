package com.example.ridehailing.it;

import com.example.ridehailing.security.JwtTokenService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Starts PostGIS, Redis and Kafka via Testcontainers (skipped automatically when Docker is unavailable). */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationIT {
    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> redis;
    static KafkaContainer kafka;

    @LocalServerPort
    int port;

    @Autowired JwtTokenService tokens;
    @Autowired TestRestTemplate rest;

    /** Bearer token attached to every TestRestTemplate call; switch identity with {@link #as}. */
    static volatile String bearer;
    private static boolean interceptorInstalled;

    @BeforeEach
    void installAuthInterceptor() {
        synchronized (AbstractIntegrationIT.class) {
            if (!interceptorInstalled) {
                rest.getRestTemplate().getInterceptors().add((req, body, exec) -> {
                    if (bearer != null && !req.getHeaders().containsKey("Authorization")) req.getHeaders().setBearerAuth(bearer);
                    return exec.execute(req, body);
                });
                interceptorInstalled = true;
            }
        }
        bearer = null;
    }

    String token(String subject, String... roles) { return tokens.issue(subject, List.of(roles)); }

    void as(String subject, String... roles) { bearer = token(subject, roles); }

    static synchronized void startAll() {
        if (postgres != null) return;
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("rides").withUsername("rides").withPassword("rides");
        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        kafka = new KafkaContainer("apache/kafka:3.8.0");
        postgres.start();
        redis.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        startAll();
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }
}
