package com.example.ridehailing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ridehailing")
public record AppProperties(Topics topics, Redis redis, Matching matching, Retry retry, Surge surge) {
    public record Topics(String driverLocations, int partitions) {
        /** Dead-letter topic used by the location consumer (same partition count as the source). */
        public String driverLocationsDlt() { return driverLocations + ".DLT"; }
    }

    /**
     * @param lastSeenKey companion ZSET (member = driverId, score = last update epoch millis)
     * @param driverTtlSeconds drivers not heard from for this long are removed from the geo index
     * @param sweepIntervalMs how often the stale-driver sweep runs
     */
    public record Redis(String geoKey, String dispatchChannel, String lastSeenKey, long driverTtlSeconds,
                        long sweepIntervalMs) {}

    public record Matching(double radiusKm, int maxCandidates) {}

    /** Retry policy of the Kafka location consumer before a record is dead-lettered. */
    public record Retry(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {}

    /**
     * @param keyPrefix Redis key prefix of per-cell demand counters
     * @param windowSeconds demand counting window (TTL refreshed on each request)
     * @param sensitivity multiplier increase per unit of demand/supply ratio above 1
     * @param maxMultiplier surge cap
     * @param baseFare flat fare component; @param perKm distance component
     */
    public record Surge(String keyPrefix, long windowSeconds, double sensitivity, double maxMultiplier,
                        double baseFare, double perKm) {}
}
