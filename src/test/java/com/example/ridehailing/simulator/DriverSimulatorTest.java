package com.example.ridehailing.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripRepository;
import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.kafka.LocationProducer;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DriverSimulatorTest {
    LocationProducer producer = mock(LocationProducer.class);
    TripRepository trips = mock(TripRepository.class);
    DriverSimulator sim = new DriverSimulator(producer, trips);

    @Test
    void publishesUpdatesForEachDriver() {
        when(trips.findFirstByDriverIdAndStatusIn(anyString(), any())).thenReturn(Optional.empty());
        sim.start(3, 37.77, -122.41, 1, 20);
        assertThat(sim.isRunning()).isTrue();
        assertThat(sim.driverCount()).isEqualTo(3);
        verify(producer, timeout(2000).atLeast(3)).send(any(LocationUpdate.class));
        sim.stop();
        assertThat(sim.isRunning()).isFalse();
    }

    @Test
    void assignedDriverMovesTowardPickup() {
        Trip t = new Trip("r", 37.80, -122.40, 37.90, -122.30);
        t.assignDriver("sim-driver-1");
        when(trips.findFirstByDriverIdAndStatusIn(anyString(), any())).thenReturn(Optional.of(t));
        sim.start(1, 37.77, -122.41, 0.0, 20);
        ArgumentCaptor<LocationUpdate> c = ArgumentCaptor.forClass(LocationUpdate.class);
        verify(producer, timeout(2000).atLeast(2)).send(c.capture());
        sim.stop();
        double d0 = Math.abs(c.getAllValues().get(0).lat() - 37.80);
        double d1 = Math.abs(c.getAllValues().get(1).lat() - 37.80);
        assertThat(d1).isLessThan(d0);
    }
}
