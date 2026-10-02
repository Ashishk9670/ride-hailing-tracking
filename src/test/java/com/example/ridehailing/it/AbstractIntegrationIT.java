package com.example.ridehailing.it;

import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationIT {
    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> redis;
    static KafkaContainer kafka;

    @LocalServerPort
    int port;

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
