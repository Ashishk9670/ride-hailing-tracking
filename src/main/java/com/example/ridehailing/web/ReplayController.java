package com.example.ridehailing.web;

import com.example.ridehailing.service.ReplayService;
import com.example.ridehailing.service.TripService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ReplayController {
    private final ReplayService replay;
    private final TripService trips;

    public ReplayController(ReplayService replay, TripService trips) {
        this.replay = replay;
        this.trips = trips;
    }

    /** Streams the stored route as SSE events (start, point*, done); speed 1 = real time, clamped to 0.1..100. */
    @GetMapping(value = "/trips/{id}/replay", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter replay(@PathVariable UUID id, @RequestParam(defaultValue = "1") double speed) {
        trips.get(id); // 404 for unknown trips
        return replay.stream(id, speed);
    }
}
