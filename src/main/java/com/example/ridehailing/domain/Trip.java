package com.example.ridehailing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip {
    @Id
    private UUID id;
    private String riderId;
    private String driverId;
    private double pickupLat;
    private double pickupLon;
    private double dropoffLat;
    private double dropoffLon;
    @Enumerated(EnumType.STRING)
    private TripStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    protected Trip() {}

    public Trip(String riderId, double pickupLat, double pickupLon, double dropoffLat, double dropoffLon) {
        this.id = UUID.randomUUID();
        this.riderId = riderId;
        this.pickupLat = pickupLat;
        this.pickupLon = pickupLon;
        this.dropoffLat = dropoffLat;
        this.dropoffLon = dropoffLon;
        this.status = TripStatus.REQUESTED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void transitionTo(TripStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal transition " + status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void assignDriver(String driverId) {
        this.driverId = driverId;
        transitionTo(TripStatus.DRIVER_ASSIGNED);
    }

    public UUID getId() { return id; }
    public String getRiderId() { return riderId; }
    public String getDriverId() { return driverId; }
    public double getPickupLat() { return pickupLat; }
    public double getPickupLon() { return pickupLon; }
    public double getDropoffLat() { return dropoffLat; }
    public double getDropoffLon() { return dropoffLon; }
    public TripStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
