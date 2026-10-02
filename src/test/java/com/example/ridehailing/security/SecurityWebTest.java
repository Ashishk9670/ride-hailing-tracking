package com.example.ridehailing.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.ridehailing.domain.Trip;
import com.example.ridehailing.kafka.LocationProducer;
import com.example.ridehailing.redis.DriverGeoIndex;
import com.example.ridehailing.service.LocationHistoryRepository;
import com.example.ridehailing.service.TripService;
import com.example.ridehailing.simulator.DriverSimulator;
import com.example.ridehailing.web.ApiExceptionHandler;
import com.example.ridehailing.web.RideController;
import com.example.ridehailing.web.SimulatorController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Real filter chain + method security with real JWTs; infrastructure beans are mocked. */
@WebMvcTest(controllers = {RideController.class, SimulatorController.class, AuthController.class})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
@TestPropertySource(properties = {
        "ridehailing.auth.jwt-secret=test-secret-test-secret-test-secret-0123",
        "ridehailing.auth.token-ttl-seconds=60",
        "ridehailing.auth.dev-token-endpoint-enabled=true"})
class SecurityWebTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @MockitoBean TripService tripService;
    @MockitoBean LocationProducer producer;
    @MockitoBean DriverGeoIndex geo;
    @MockitoBean LocationHistoryRepository history;
    @MockitoBean DriverSimulator simulator;

    String bearer(String sub, String... roles) { return "Bearer " + tokens.issue(sub, List.of(roles)); }

    static final String RIDE = "{\"riderId\":\"alice\",\"pickupLat\":1,\"pickupLon\":2,\"dropoffLat\":3,\"dropoffLon\":4}";
    static final String LOC = "{\"lat\":1,\"lon\":2,\"heading\":0,\"speedKmh\":1}";

    @Test
    void anonymousIsRejectedWith401() throws Exception {
        mvc.perform(post("/api/rides").contentType(MediaType.APPLICATION_JSON).content(RIDE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/drivers/nearby?lat=1&lon=1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/simulator/stop")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageAndTamperedTokensAreRejected() throws Exception {
        mvc.perform(get("/api/drivers/nearby?lat=1&lon=1").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        String token = tokens.issue("alice", List.of("RIDER"));
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        mvc.perform(get("/api/drivers/nearby?lat=1&lon=1").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void riderCanRequestRideForSelfOnly() throws Exception {
        when(tripService.requestRide(any())).thenReturn(new Trip("alice", 1, 2, 3, 4));
        mvc.perform(post("/api/rides").header("Authorization", bearer("alice", "RIDER"))
                        .contentType(MediaType.APPLICATION_JSON).content(RIDE))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/rides").header("Authorization", bearer("mallory", "RIDER"))
                        .contentType(MediaType.APPLICATION_JSON).content(RIDE))
                .andExpect(status().isForbidden());
    }

    @Test
    void driverCannotRequestRides() throws Exception {
        mvc.perform(post("/api/rides").header("Authorization", bearer("alice", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content(RIDE))
                .andExpect(status().isForbidden());
    }

    @Test
    void driverMayOnlyReportOwnLocation() throws Exception {
        mvc.perform(post("/api/drivers/d1/location").header("Authorization", bearer("d1", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content(LOC))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/drivers/d1/location").header("Authorization", bearer("d2", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content(LOC))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/drivers/d1/location").header("Authorization", bearer("d1", "RIDER"))
                        .contentType(MediaType.APPLICATION_JSON).content(LOC))
                .andExpect(status().isForbidden());
    }

    @Test
    void simulatorNeedsAdmin() throws Exception {
        when(simulator.isRunning()).thenReturn(false);
        mvc.perform(post("/api/simulator/stop").header("Authorization", bearer("alice", "RIDER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/simulator/stop").header("Authorization", bearer("ops", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void devTokenEndpointIssuesUsableTokensAndRejectsUnknownRoles() throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"bob\",\"roles\":[\"RIDER\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/drivers/nearby?lat=1&lon=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"bob\",\"roles\":[\"SUPERUSER\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unlistedPathsAreDenied() throws Exception {
        mvc.perform(get("/actuator/env").header("Authorization", bearer("ops", "ADMIN")))
                .andExpect(status().isForbidden());
    }
}
