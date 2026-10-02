package com.example.ridehailing.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/** Surfaces dead-lettered location records for operators (raw bytes: they may not be valid JSON). */
@Component
public class DeadLetterConsumer {
    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    @KafkaListener(topics = "${ridehailing.topics.driver-locations}.DLT", groupId = "location-dlt-audit",
            properties = {"value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer"})
    public void onDeadLetter(ConsumerRecord<String, byte[]> rec) {
        var h = rec.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        log.warn("DLT record key={} originalTopic={} reason={} payload={}", rec.key(), header(rec, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                h == null ? "?" : new String(h.value(), StandardCharsets.UTF_8),
                rec.value() == null ? "null" : new String(rec.value(), StandardCharsets.UTF_8));
    }

    private static String header(ConsumerRecord<?, ?> rec, String name) {
        var h = rec.headers().lastHeader(name);
        return h == null ? "?" : new String(h.value(), StandardCharsets.UTF_8);
    }
}
