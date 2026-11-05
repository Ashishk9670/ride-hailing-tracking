package com.example.ridehailing.redis;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.service.EtaModel;
import com.example.ridehailing.service.SurgePricing;
import java.time.Duration;
import java.util.OptionalDouble;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Recent average driver speed per grid cell, kept as a TTL'd exponential moving average in Redis. */
@Component
public class CellSpeedTracker {
    private final StringRedisTemplate redis;
    private final AppProperties.Eta cfg;

    public CellSpeedTracker(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.cfg = props.eta();
    }

    /** Folds a moving sample into the cell average (best effort: concurrent writers may lose one sample). */
    public void record(double lat, double lon, double speedKmh) {
        if (!(speedKmh > 0) || speedKmh > 200) return; // ignore stationary or implausible readings
        String key = cfg.speedKeyPrefix() + SurgePricing.cellId(lat, lon);
        String prev = redis.opsForValue().get(key);
        double next = EtaModel.ema(prev == null ? null : Double.valueOf(prev), speedKmh, cfg.emaAlpha());
        redis.opsForValue().set(key, Double.toString(next), Duration.ofSeconds(cfg.speedTtlSeconds()));
    }

    public OptionalDouble averageKmh(double lat, double lon) {
        String v = redis.opsForValue().get(cfg.speedKeyPrefix() + SurgePricing.cellId(lat, lon));
        return v == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(v));
    }
}
