package com.example.ridehailing.redis;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.dto.NearbyDriver;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

/** Redis GEO index of AVAILABLE drivers (GEOADD / GEOSEARCH / ZREM-as-claim). */
@Component
public class DriverGeoIndex {
    private final StringRedisTemplate redis;
    /** Atomically drops every driver whose last-seen score is older than the cutoff from both structures. */
    private static final DefaultRedisScript<Long> EXPIRE_SCRIPT = new DefaultRedisScript<>("""
            local ids = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1], 'LIMIT', 0, 1000)
            local n = 0
            for _, id in ipairs(ids) do
              n = n + redis.call('ZREM', KEYS[1], id)
              redis.call('ZREM', KEYS[2], id)
            end
            return n
            """, Long.class);

    private final String key;
    private final String lastSeenKey;
    private final long ttlMillis;

    public DriverGeoIndex(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.key = props.redis().geoKey();
        this.lastSeenKey = props.redis().lastSeenKey();
        this.ttlMillis = props.redis().driverTtlSeconds() * 1000L;
    }

    public void upsert(String driverId, double lat, double lon) {
        upsert(driverId, lat, lon, System.currentTimeMillis());
    }

    public void upsert(String driverId, double lat, double lon, long nowMillis) {
        redis.opsForGeo().add(key, new Point(lon, lat), driverId);
        redis.opsForZSet().add(lastSeenKey, driverId, nowMillis);
    }

    /** Removes drivers not seen within the TTL; returns how many were evicted from the geo index. */
    public int expireStale(long nowMillis) {
        Long n = redis.execute(EXPIRE_SCRIPT, List.of(key, lastSeenKey), String.valueOf(nowMillis - ttlMillis));
        return n == null ? 0 : n.intValue();
    }

    public boolean isIndexed(String driverId) {
        return redis.opsForZSet().score(lastSeenKey, driverId) != null;
    }

    public List<NearbyDriver> findNearest(double lat, double lon, double radiusKm, int limit) {
        GeoResults<GeoLocation<String>> results = redis.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(lon, lat),
                new Distance(radiusKm, Metrics.KILOMETERS),
                GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().includeDistance().sortAscending().limit(limit));
        List<NearbyDriver> out = new ArrayList<>();
        if (results == null) return out;
        for (GeoResult<GeoLocation<String>> r : results) {
            Point p = r.getContent().getPoint();
            out.add(new NearbyDriver(r.getContent().getName(),
                    r.getDistance().in(Metrics.KILOMETERS).getValue() * 1000.0,
                    p == null ? lat : p.getY(), p == null ? lon : p.getX()));
        }
        return out;
    }

    /** Atomically claims a driver: only one caller gets true because ZREM returns 1 once. */
    public boolean claim(String driverId) {
        Long removed = redis.opsForGeo().remove(key, driverId);
        redis.opsForZSet().remove(lastSeenKey, driverId);
        return removed != null && removed > 0;
    }

    public void remove(String driverId) {
        redis.opsForGeo().remove(key, driverId);
        redis.opsForZSet().remove(lastSeenKey, driverId);
    }
}
