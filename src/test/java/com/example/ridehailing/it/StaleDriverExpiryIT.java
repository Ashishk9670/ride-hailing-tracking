package com.example.ridehailing.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ridehailing.dto.NearbyDriver;
import com.example.ridehailing.redis.DriverGeoIndex;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Real Redis: drivers that stop reporting are evicted from the GEO index and its companion last-seen ZSET. */
class StaleDriverExpiryIT extends AbstractIntegrationIT {
    @Autowired DriverGeoIndex index;

    List<String> idsNear(double lat, double lon) {
        return index.findNearest(lat, lon, 2, 20).stream().map(NearbyDriver::driverId).toList();
    }

    @Test
    void staleDriversAreEvictedAndFreshOnesKept() {
        long now = System.currentTimeMillis();
        double lat = -45.0, lon = 170.0; // remote corner of the world: no other test uses it
        index.upsert("stale-1", lat, lon, now - 120_000);
        index.upsert("fresh-1", lat + 0.001, lon, now);
        assertThat(idsNear(lat, lon)).contains("stale-1", "fresh-1");

        index.expireStale(now); // default TTL is 30s in application.yml

        assertThat(idsNear(lat, lon)).contains("fresh-1").doesNotContain("stale-1");
        assertThat(index.isIndexed("stale-1")).isFalse();
        assertThat(index.isIndexed("fresh-1")).isTrue();
    }

    @Test
    void claimedDriverIsRemovedFromBothStructures() {
        double lat = -46.0, lon = 171.0;
        index.upsert("claim-1", lat, lon);
        assertThat(index.claim("claim-1")).isTrue();
        assertThat(index.claim("claim-1")).isFalse();
        assertThat(index.isIndexed("claim-1")).isFalse();
    }
}
