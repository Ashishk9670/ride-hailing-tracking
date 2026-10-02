package com.example.ridehailing.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** Business metrics exposed through Micrometer (Prometheus names in comments). */
@Component
public class AppMetrics {
    private final MeterRegistry registry;
    private final Counter received;
    private final Counter processed;
    private final Counter dlt;
    private final Counter expired;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
        // driver_location_updates_total{stage="received|processed"}: rate() gives location updates/sec
        this.received = Counter.builder("driver.location.updates").tag("stage", "received")
                .description("Driver location updates accepted by the API").register(registry);
        this.processed = Counter.builder("driver.location.updates").tag("stage", "processed")
                .description("Driver location updates consumed from Kafka").register(registry);
        // location_dlt_total
        this.dlt = Counter.builder("location.dlt")
                .description("Location records sent to the dead-letter topic").register(registry);
        // drivers_expired_total
        this.expired = Counter.builder("drivers.expired")
                .description("Stale drivers evicted from the Redis geo index").register(registry);
    }

    public void locationReceived() { received.increment(); }

    public void locationProcessed() { processed.increment(); }

    public void deadLettered() { dlt.increment(); }

    public void driversExpired(int n) { expired.increment(n); }

    public Timer.Sample startMatch() { return Timer.start(registry); }

    /** rides_matches_total{result} and rides_match_latency_seconds_bucket{result}. */
    public void recordMatch(Timer.Sample sample, boolean matched) {
        String result = matched ? "matched" : "no_driver";
        registry.counter("rides.matches", "result", result).increment();
        sample.stop(Timer.builder("rides.match.latency").description("Time to find and claim a driver")
                .tag("result", result).publishPercentileHistogram().register(registry));
    }
}
