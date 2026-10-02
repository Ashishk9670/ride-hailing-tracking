package com.example.ridehailing.kafka;

import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.service.LocationIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LocationConsumer {
    private static final Logger log = LoggerFactory.getLogger(LocationConsumer.class);
    private final LocationIngestService ingest;

    public LocationConsumer(LocationIngestService ingest) { this.ingest = ingest; }

    @KafkaListener(topics = "${ridehailing.topics.driver-locations}")
    public void onMessage(LocationUpdate update) {
        try {
            ingest.process(update);
        } catch (Exception e) {
            log.error("Failed processing location update for {}", update.driverId(), e);
        }
    }
}
