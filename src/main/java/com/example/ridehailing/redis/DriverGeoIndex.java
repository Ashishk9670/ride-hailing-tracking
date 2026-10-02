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
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

/** Redis GEO index of AVAILABLE drivers (GEOADD / GEOSEARCH / ZREM-as-claim). */
@Component
public class DriverGeoIndex {
    private final StringRedisTemplate redis;
    private final String key;

    public DriverGeoIndex(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.key = props.redis().geoKey();
    }

    public void upsert(String driverId, double lat, double lon) {
        redis.opsForGeo().add(key, new Point(lon, lat), driverId);
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
        return removed != null && removed > 0;
    }

    public void remove(String driverId) {
        redis.opsForGeo().remove(key, driverId);
    }
}
