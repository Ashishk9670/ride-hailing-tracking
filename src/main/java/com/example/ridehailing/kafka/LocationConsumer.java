package com.example.ridehailing.kafka;

import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.service.LocationIngestService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LocationConsumer {
    private final LocationIngestService ingest;

    public LocationConsumer(LocationIngestService ingest) { this.ingest = ingest; }

    @KafkaListener(topics = "${ridehailing.topics.driver-locations}")
    public void onMessage(LocationUpdate update) {
        // Exceptions propagate to the container's DefaultErrorHandler: retry with backoff, then dead-letter.
        ingest.process(update);
    }
}
