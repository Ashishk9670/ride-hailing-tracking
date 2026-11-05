package com.example.ridehailing.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AppMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AppMetrics metrics = new AppMetrics(registry);

    @Test
    void locationLagRecordsElapsedTime() {
        metrics.locationLag(System.currentTimeMillis() - 2_000);
        var t = registry.get("driver.location.lag").timer();
        assertThat(t.count()).isEqualTo(1);
        assertThat(t.totalTime(TimeUnit.MILLISECONDS)).isBetween(1_900.0, 10_000.0);
    }

    @Test
    void futureTimestampsAreClampedToZero() {
        metrics.locationLag(System.currentTimeMillis() + 60_000);
        assertThat(registry.get("driver.location.lag").timer().totalTime(TimeUnit.MILLISECONDS)).isZero();
    }
}
