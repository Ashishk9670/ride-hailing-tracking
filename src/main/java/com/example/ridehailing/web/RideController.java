package com.example.ridehailing.web;

import com.example.ridehailing.dto.*;
import com.example.ridehailing.kafka.LocationProducer;
import com.example.ridehailing.redis.DriverGeoIndex;
import com.example.ridehailing.service.LocationHistoryRepository;
import com.example.ridehailing.service.TripService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RideController {
    private final TripService tripService;
    private final LocationProducer producer;
    private final DriverGeoIndex geoIndex;
    private final LocationHistoryRepository history;

    public RideController(TripService tripService, LocationProducer producer, DriverGeoIndex geoIndex,
                          LocationHistoryRepository history) {
        this.tripService = tripService;
        this.producer = producer;
        this.geoIndex = geoIndex;
        this.history = history;
    }

    @PostMapping("/rides")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("#req.riderId() == authentication.name")
    public TripView requestRide(@Valid @RequestBody RideRequest req) {
        return TripView.of(tripService.requestRide(req));
    }

    @GetMapping("/trips/{id}")
    public TripView getTrip(@PathVariable UUID id) { return TripView.of(tripService.get(id)); }

    @PatchMapping("/trips/{id}/status")
    public TripView updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest req) {
        return TripView.of(tripService.updateStatus(id, req.status()));
    }

    @GetMapping("/trips/{id}/history")
    public Map<String, Object> history(@PathVariable UUID id) {
        return Map.of("points", history.findByTrip(id), "routeLengthMeters", history.routeLengthMeters(id));
    }

    /** Driver app endpoint: pushes into the Kafka firehose. */
    @PostMapping("/drivers/{driverId}/location")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("#driverId == authentication.name")
    public void driverLocation(@PathVariable String driverId, @Valid @RequestBody DriverLocationRequest req) {
        producer.send(new LocationUpdate(driverId, null, req.lat(), req.lon(), req.heading(), req.speedKmh(),
                System.currentTimeMillis()));
    }

    @GetMapping("/drivers/nearby")
    public List<NearbyDriver> nearby(@RequestParam double lat, @RequestParam double lon,
                                     @RequestParam(defaultValue = "5") double radiusKm,
                                     @RequestParam(defaultValue = "10") int limit) {
        return geoIndex.findNearest(lat, lon, radiusKm, limit);
    }
}
