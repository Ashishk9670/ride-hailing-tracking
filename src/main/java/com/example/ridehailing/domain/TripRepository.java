package com.example.ridehailing.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findFirstByDriverIdAndStatusIn(String driverId, Collection<TripStatus> statuses);
}
