package com.example.ridehailing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ridehailing")
public record AppProperties(Topics topics, Redis redis, Matching matching) {
    public record Topics(String driverLocations, int partitions) {}
    public record Redis(String geoKey, String dispatchChannel) {}
    public record Matching(double radiusKm, int maxCandidates) {}
}
