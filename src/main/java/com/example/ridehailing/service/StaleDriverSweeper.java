package com.example.ridehailing.service;

import com.example.ridehailing.metrics.AppMetrics;
import com.example.ridehailing.redis.DriverGeoIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically evicts drivers that stopped reporting from the Redis geo index. */
@Component
public class StaleDriverSweeper {
    private static final Logger log = LoggerFactory.getLogger(StaleDriverSweeper.class);
    private final DriverGeoIndex geoIndex;
    private final AppMetrics metrics;

    public StaleDriverSweeper(DriverGeoIndex geoIndex, AppMetrics metrics) {
        this.geoIndex = geoIndex;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${ridehailing.redis.sweep-interval-ms}")
    public void sweep() {
        try {
            int n = geoIndex.expireStale(System.currentTimeMillis());
            if (n > 0) {
                metrics.driversExpired(n);
                log.info("Expired {} stale driver(s) from the geo index", n);
            }
        } catch (RuntimeException e) {
            log.warn("Stale driver sweep failed", e);
        }
    }
}
