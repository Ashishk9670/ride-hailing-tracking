package com.example.ridehailing.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.ridehailing.dto.TripEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

class DispatchEventSubscriberTest {
    TripEventBroadcaster broadcaster = mock(TripEventBroadcaster.class);
    DispatchEventSubscriber sub = new DispatchEventSubscriber(new ObjectMapper(), broadcaster);

    @Test
    void forwardsValidMessage() {
        String json = "{\"type\":\"DRIVER_ASSIGNED\",\"tripId\":\"t\",\"driverId\":\"d\",\"status\":\"DRIVER_ASSIGNED\",\"timestamp\":1}";
        sub.onMessage(new DefaultMessage("c".getBytes(), json.getBytes()), null);
        verify(broadcaster).publishEvent(any(TripEvent.class));
    }

    @Test
    void ignoresMalformedMessage() {
        sub.onMessage(new DefaultMessage("c".getBytes(), "nope".getBytes()), null);
        verifyNoInteractions(broadcaster);
    }
}
