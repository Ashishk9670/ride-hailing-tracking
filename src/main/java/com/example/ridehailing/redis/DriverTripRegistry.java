package com.example.ridehailing.redis;

import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** driverId -> active tripId mapping in Redis. */
@Component
public class DriverTripRegistry {
    private static final String PREFIX = "driver:trip:";
    private final StringRedisTemplate redis;

    public DriverTripRegistry(StringRedisTemplate redis) { this.redis = redis; }

    public void assign(String driverId, String tripId) { redis.opsForValue().set(PREFIX + driverId, tripId); }

    public Optional<String> activeTrip(String driverId) {
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + driverId));
    }

    public void release(String driverId) { redis.delete(PREFIX + driverId); }
}
