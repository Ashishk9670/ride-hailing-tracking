package com.example.ridehailing.web;

import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.dto.NearbyDriver;
import com.example.ridehailing.dto.TripEvent;
import com.example.ridehailing.dto.TripView;
import com.example.ridehailing.redis.DriverGeoIndex;
import com.example.ridehailing.service.TripEventBroadcaster;
import com.example.ridehailing.service.TripService;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
public class TripGraphQlController {
    private final TripService tripService;
    private final TripEventBroadcaster broadcaster;
    private final DriverGeoIndex geoIndex;

    public TripGraphQlController(TripService tripService, TripEventBroadcaster broadcaster, DriverGeoIndex geoIndex) {
        this.tripService = tripService;
        this.broadcaster = broadcaster;
        this.geoIndex = geoIndex;
    }

    @QueryMapping
    public TripView trip(@Argument String id) { return TripView.of(tripService.get(UUID.fromString(id))); }

    @QueryMapping
    public List<NearbyDriver> nearbyDrivers(@Argument double lat, @Argument double lon, @Argument double radiusKm) {
        return geoIndex.findNearest(lat, lon, radiusKm, 20);
    }

    @SubscriptionMapping
    public Flux<LocationUpdate> tripLocation(@Argument String tripId) { return broadcaster.locations(tripId); }

    @SubscriptionMapping
    public Flux<TripEvent> tripStatus(@Argument String tripId) { return broadcaster.events(tripId); }
}
