package com.example.ridehailing.service;

import com.example.ridehailing.dto.TripEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/** Receives dispatch events from Redis pub/sub (works across app instances) and forwards to subscribers. */
@Component
public class DispatchEventSubscriber implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(DispatchEventSubscriber.class);
    private final ObjectMapper mapper;
    private final TripEventBroadcaster broadcaster;

    public DispatchEventSubscriber(ObjectMapper mapper, TripEventBroadcaster broadcaster) {
        this.mapper = mapper;
        this.broadcaster = broadcaster;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            broadcaster.publishEvent(mapper.readValue(message.getBody(), TripEvent.class));
        } catch (Exception e) {
            log.warn("Bad dispatch event", e);
        }
    }
}
