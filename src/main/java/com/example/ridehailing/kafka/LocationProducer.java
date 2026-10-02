package com.example.ridehailing.kafka;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.metrics.AppMetrics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class LocationProducer {
    private final KafkaTemplate<String, LocationUpdate> kafka;
    private final String topic;

    private final AppMetrics metrics;

    public LocationProducer(KafkaTemplate<String, LocationUpdate> kafka, AppProperties props, AppMetrics metrics) {
        this.metrics = metrics;
        this.kafka = kafka;
        this.topic = props.topics().driverLocations();
    }

    /** Keyed by driverId so a driver's updates stay ordered within a partition. */
    public void send(LocationUpdate u) {
        kafka.send(topic, u.driverId(), u);
        metrics.locationReceived();
    }
}
