package com.example.ridehailing.service;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.dto.TripEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Publishes dispatch events on a Redis pub/sub channel. */
@Component
public class DispatchEventPublisher {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String channel;

    public DispatchEventPublisher(StringRedisTemplate redis, ObjectMapper mapper, AppProperties props) {
        this.redis = redis;
        this.mapper = mapper;
        this.channel = props.redis().dispatchChannel();
    }

    public void publish(TripEvent e) {
        try {
            redis.convertAndSend(channel, mapper.writeValueAsString(e));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
