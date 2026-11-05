package com.example.ridehailing.service;

import com.example.ridehailing.dto.LocationUpdate;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Streams a trip's stored location_history as Server-Sent Events, paced by the recorded timestamps / speed. */
@Service
public class ReplayService {
    public static final double MIN_SPEED = 0.1;
    public static final double MAX_SPEED = 100;
    /** Idle stretches in the recording are compressed to at most this long (before dividing by speed). */
    static final long MAX_GAP_MS = 5_000;

    @FunctionalInterface public interface Sleeper { void sleep(long millis) throws InterruptedException; }
    @FunctionalInterface public interface Sink { void accept(int index, LocationUpdate point) throws IOException; }

    private final LocationHistoryRepository history;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ReplayService(LocationHistoryRepository history) { this.history = history; }

    public static double clampSpeed(double speed) {
        if (Double.isNaN(speed)) return 1;
        return Math.min(Math.max(speed, MIN_SPEED), MAX_SPEED);
    }

    /** Wall-clock pause before emitting the next point. */
    public static long delayMillis(long prevTs, long ts, double speed) {
        long gap = Math.min(Math.max(ts - prevTs, 0), MAX_GAP_MS);
        return Math.round(gap / clampSpeed(speed));
    }

    /** Pacing core, separated from SSE so it is unit-testable. Returns the number of points emitted. */
    public static int replay(List<LocationUpdate> points, double speed, Sleeper sleeper, Sink sink,
                             java.util.function.BooleanSupplier cancelled) throws IOException, InterruptedException {
        int n = 0;
        for (int i = 0; i < points.size() && !cancelled.getAsBoolean(); i++) {
            if (i > 0) {
                long d = delayMillis(points.get(i - 1).timestamp(), points.get(i).timestamp(), speed);
                if (d > 0) sleeper.sleep(d);
            }
            sink.accept(i, points.get(i));
            n++;
        }
        return n;
    }

    public SseEmitter stream(UUID tripId, double speed) {
        double s = clampSpeed(speed);
        List<LocationUpdate> points = history.findByTrip(tripId);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean cancelled = new AtomicBoolean();
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
        executor.submit(() -> {
            try {
                emitter.send(SseEmitter.event().name("start").data(
                        new Start(points.size(), s), MediaType.APPLICATION_JSON));
                replay(points, s, Thread::sleep,
                        (i, p) -> emitter.send(SseEmitter.event().id(String.valueOf(i)).name("point")
                                .data(p, MediaType.APPLICATION_JSON)),
                        cancelled::get);
                if (!cancelled.get()) {
                    emitter.send(SseEmitter.event().name("done").data(new Start(points.size(), s), MediaType.APPLICATION_JSON));
                }
                emitter.complete();
            } catch (IOException | RuntimeException e) {
                emitter.completeWithError(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.complete();
            }
        });
        return emitter;
    }

    public record Start(int points, double speed) {}
}
