package com.example.ridehailing.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.domain.TripStatus;
import com.example.ridehailing.kafka.LocationProducer;
import com.example.ridehailing.redis.DriverGeoIndex;
import com.example.ridehailing.service.LocationHistoryRepository;
import com.example.ridehailing.service.TripService;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RideControllerTest {
    TripService service = mock(TripService.class);
    LocationProducer producer = mock(LocationProducer.class);
    DriverGeoIndex geo = mock(DriverGeoIndex.class);
    LocationHistoryRepository history = mock(LocationHistoryRepository.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new RideController(service, producer, geo, history))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void requestRideReturns201() throws Exception {
        Trip t = new Trip("r1", 1, 2, 3, 4);
        when(service.requestRide(any())).thenReturn(t);
        mvc.perform(post("/api/rides").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riderId\":\"r1\",\"pickupLat\":1,\"pickupLon\":2,\"dropoffLat\":3,\"dropoffLon\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riderId").value("r1"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void driverLocationAcceptedAndPublished() throws Exception {
        mvc.perform(post("/api/drivers/d1/location").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":1,\"lon\":2,\"heading\":0,\"speedKmh\":10}"))
                .andExpect(status().isAccepted());
        verify(producer).send(any());
    }

    @Test
    void unknownTripIs404() throws Exception {
        when(service.get(any())).thenThrow(new NoSuchElementException("nope"));
        mvc.perform(get("/api/trips/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void illegalTransitionIs409() throws Exception {
        when(service.updateStatus(any(), any())).thenThrow(new IllegalStateException("bad"));
        mvc.perform(patch("/api/trips/" + UUID.randomUUID() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void statusUpdateOk() throws Exception {
        Trip t = new Trip("r", 1, 1, 2, 2);
        t.assignDriver("d");
        when(service.updateStatus(any(), eq(TripStatus.DRIVER_ARRIVED))).thenReturn(t);
        mvc.perform(patch("/api/trips/" + t.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRIVER_ARRIVED\"}"))
                .andExpect(status().isOk());
    }
}
