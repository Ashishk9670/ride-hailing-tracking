package com.example.ridehailing.simulator;

import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripRepository;
import com.example.ridehailing.domain.TripStatus;
import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.kafka.LocationProducer;
import com.example.ridehailing.service.GeoUtils;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** Publishes synthetic driver locations to Kafka. Idle drivers wander; assigned drivers drive to pickup/dropoff. */
@Component
public class DriverSimulator {
    private static final double STEP_METERS = 60;
    private final LocationProducer producer;
    private final TripRepository trips;
    private final Map<String, double[]> drivers = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private ScheduledExecutorService scheduler;

    public DriverSimulator(LocationProducer producer, TripRepository trips) {
        this.producer = producer;
        this.trips = trips;
    }

    public synchronized void start(int count, double centerLat, double centerLon, double spreadKm, long intervalMs) {
        stop();
        for (int i = 1; i <= count; i++) {
            double lat = centerLat + (random.nextDouble() - 0.5) * spreadKm / 111.0 * 2;
            double lon = centerLon + (random.nextDouble() - 0.5) * spreadKm / 111.0 * 2;
            drivers.put("sim-driver-" + i, new double[]{lat, lon});
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::tick, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        drivers.clear();
    }

    public boolean isRunning() { return scheduler != null; }

    public int driverCount() { return drivers.size(); }

    void tick() {
        drivers.forEach((id, pos) -> {
            try {
                double[] target = targetFor(id, pos);
                double[] next = target == null
                        ? new double[]{pos[0] + (random.nextDouble() - 0.5) * 0.0006, pos[1] + (random.nextDouble() - 0.5) * 0.0006}
                        : GeoUtils.moveToward(pos[0], pos[1], target[0], target[1], STEP_METERS);
                double heading = target == null ? random.nextDouble() * 360
                        : GeoUtils.bearing(pos[0], pos[1], target[0], target[1]);
                double speed = target == null ? 15 : STEP_METERS / 1000.0 * 3600 / 1.0;
                pos[0] = next[0];
                pos[1] = next[1];
                producer.send(new LocationUpdate(id, null, next[0], next[1], heading, speed, System.currentTimeMillis()));
            } catch (Exception ignored) {
                // keep simulating even if one tick fails
            }
        });
    }

    private double[] targetFor(String driverId, double[] pos) {
        Optional<Trip> t = trips.findFirstByDriverIdAndStatusIn(driverId, TripStatus.active());
        if (t.isEmpty()) return null;
        Trip trip = t.get();
        return trip.getStatus() == TripStatus.IN_PROGRESS
                ? new double[]{trip.getDropoffLat(), trip.getDropoffLon()}
                : new double[]{trip.getPickupLat(), trip.getPickupLon()};
    }
}
