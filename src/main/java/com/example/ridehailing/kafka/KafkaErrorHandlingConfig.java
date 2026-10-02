package com.example.ridehailing.kafka;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.metrics.AppMetrics;
import com.example.ridehailing.service.InvalidLocationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.apache.kafka.clients.admin.NewTopic;

/**
 * Retry with exponential backoff, then publish to {@code <topic>.DLT}. Poison pills that fail to
 * deserialize (ErrorHandlingDeserializer) are dead-lettered immediately with their raw bytes.
 */
@Configuration
public class KafkaErrorHandlingConfig implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);
    private DefaultKafkaProducerFactory<String, byte[]> rawFactory;

    @Bean
    NewTopic driverLocationsDltTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().driverLocationsDlt())
                .partitions(props.topics().partitions()).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, LocationUpdate> jsonTemplate, KafkaProperties kafkaProps,
                                          AppProperties props, AppMetrics metrics) {
        Map<String, Object> raw = kafkaProps.buildProducerProperties(null);
        raw.put("key.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
        raw.put("value.serializer", ByteArraySerializer.class);
        rawFactory = new DefaultKafkaProducerFactory<>(raw);
        KafkaTemplate<String, byte[]> bytesTemplate = new KafkaTemplate<>(rawFactory);

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, jsonTemplate);
        String dlt = props.topics().driverLocationsDlt();
        DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(templates,
                (rec, ex) -> new TopicPartition(dlt, rec.partition()));
        ConsumerRecordRecoverer recoverer = (rec, ex) -> {
            metrics.deadLettered();
            log.warn("Dead-lettering record {}-{}@{} after failure: {}", rec.topic(), rec.partition(), rec.offset(),
                    ex.getMessage());
            publisher.accept(rec, ex);
        };

        AppProperties.Retry r = props.retry();
        ExponentialBackOff backOff = new ExponentialBackOff(r.initialIntervalMs(), r.multiplier());
        backOff.setMaxInterval(r.maxIntervalMs());
        backOff.setMaxAttempts(Math.max(0, r.maxAttempts() - 1)); // maxAttempts counts the first delivery
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidLocationException.class);
        return handler;
    }

    @Override
    public void destroy() {
        if (rawFactory != null) rawFactory.destroy();
    }
}
