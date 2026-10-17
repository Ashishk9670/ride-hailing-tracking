package com.example.ridehailing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ridehailing.dto.LocationUpdate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayServiceTest {
    static LocationUpdate at(long ts) { return new LocationUpdate("d", "t", 1, 1, 0, 0, ts); }

    @Test
    void delayScalesWithSpeedAndCapsLongGaps() {
        assertThat(ReplayService.delayMillis(1000, 3000, 1)).isEqualTo(2000);
        assertThat(ReplayService.delayMillis(1000, 3000, 4)).isEqualTo(500);
        assertThat(ReplayService.delayMillis(0, 600_000, 1)).isEqualTo(ReplayService.MAX_GAP_MS);
        assertThat(ReplayService.delayMillis(5000, 1000, 1)).isZero(); // out-of-order timestamps never go negative
    }

    @Test
    void speedIsClamped() {
        assertThat(ReplayService.clampSpeed(0)).isEqualTo(ReplayService.MIN_SPEED);
        assertThat(ReplayService.clampSpeed(1e9)).isEqualTo(ReplayService.MAX_SPEED);
        assertThat(ReplayService.clampSpeed(Double.NaN)).isEqualTo(1);
    }

    @Test
    void replayEmitsAllPointsInOrderWithPacing() throws Exception {
        List<Long> sleeps = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();
        int n = ReplayService.replay(List.of(at(0), at(1000), at(3000)), 2, sleeps::add,
                (i, p) -> seen.add(i), () -> false);
        assertThat(n).isEqualTo(3);
        assertThat(seen).containsExactly(0, 1, 2);
        assertThat(sleeps).containsExactly(500L, 1000L);
    }

    @Test
    void replayStopsWhenCancelled() throws Exception {
        List<Integer> seen = new ArrayList<>();
        int n = ReplayService.replay(List.of(at(0), at(10), at(20)), 1, ms -> {},
                (i, p) -> seen.add(i), () -> seen.size() >= 1);
        assertThat(n).isEqualTo(1);
    }
}
