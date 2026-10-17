package com.example.ridehailing.service;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.redis.DriverGeoIndex;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Surge per grid cell: demand = ride requests in the cell during a sliding TTL window (Redis counter),
 * supply = available drivers inside the cell (Redis GEOSEARCH BYBOX on the driver geo index).
 */
@Service
public class SurgeService {
    public record Quote(String cell, long demand, long supply, double multiplier) {}

    private final StringRedisTemplate redis;
    private final DriverGeoIndex geoIndex;
    private final AppProperties.Surge cfg;

    public SurgeService(StringRedisTemplate redis, DriverGeoIndex geoIndex, AppProperties props) {
        this.redis = redis;
        this.geoIndex = geoIndex;
        this.cfg = props.surge();
    }

    /** Records one unit of demand for the pickup cell and returns the resulting quote. */
    public Quote recordDemandAndQuote(double lat, double lon) {
        String cell = SurgePricing.cellId(lat, lon);
        String key = cfg.keyPrefix() + cell;
        Long demand = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofSeconds(cfg.windowSeconds()));
        return quote(cell, demand == null ? 1 : demand, lat, lon);
    }

    /** Read-only quote (does not count as demand). */
    public Quote peek(double lat, double lon) {
        String cell = SurgePricing.cellId(lat, lon);
        String v = redis.opsForValue().get(cfg.keyPrefix() + cell);
        return quote(cell, v == null ? 0 : Long.parseLong(v), lat, lon);
    }

    private Quote quote(String cell, long demand, double lat, double lon) {
        double[] c = SurgePricing.cellCenter(lat, lon);
        long supply = geoIndex.countInCell(c[0], c[1], SurgePricing.CELL_DEG);
        return new Quote(cell, demand, supply,
                SurgePricing.multiplier(demand, supply, cfg.sensitivity(), cfg.maxMultiplier()));
    }
}
