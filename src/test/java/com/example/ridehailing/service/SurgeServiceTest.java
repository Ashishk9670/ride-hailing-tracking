package com.example.ridehailing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.redis.DriverGeoIndex;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class SurgeServiceTest {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> ops = mock(ValueOperations.class);
    DriverGeoIndex geo = mock(DriverGeoIndex.class);
    SurgeService service;

    SurgeServiceTest() {
        when(redis.opsForValue()).thenReturn(ops);
        AppProperties props = new AppProperties(null, null, null, null,
                new AppProperties.Surge("s:", 120, 0.5, 3.0, 2.5, 1.2));
        service = new SurgeService(redis, geo, props);
    }

    @Test
    void highDemandLowSupplySurges() {
        when(ops.increment(any(String.class))).thenReturn(6L);
        when(geo.countInCell(anyDouble(), anyDouble(), anyDouble())).thenReturn(2L);
        SurgeService.Quote q = service.recordDemandAndQuote(37.77, -122.41);
        assertThat(q.demand()).isEqualTo(6);
        assertThat(q.supply()).isEqualTo(2);
        assertThat(q.multiplier()).isEqualTo(2.0);
        verify(redis).expire(startsWith("s:"), eq(Duration.ofSeconds(120)));
    }

    @Test
    void peekDoesNotIncrement() {
        when(ops.get(any(String.class))).thenReturn(null);
        when(geo.countInCell(anyDouble(), anyDouble(), anyDouble())).thenReturn(0L);
        assertThat(service.peek(1, 1).multiplier()).isEqualTo(1.0);
        verify(ops, never()).increment(any(String.class));
    }
}
