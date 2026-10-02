package com.example.ridehailing.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.ridehailing.redis.DriverGeoIndex;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.KafkaHeaders;

/** Poison pills and unprocessable records end up on driver-locations.DLT; the consumer keeps working. */
class DeadLetterIT extends AbstractIntegrationIT {
    @Autowired MeterRegistry meters;
    @Autowired DriverGeoIndex index;

    @Test
    void malformedAndInvalidRecordsAreDeadLettered() {
        double before = meters.counter("location.dlt").count();
        String badJson = "{{{ definitely not json";
        String badCoords = "{\"driverId\":\"dlt-bad\",\"lat\":999,\"lon\":0,\"heading\":0,\"speedKmh\":0,\"timestamp\":1}";
        String good = "{\"driverId\":\"dlt-good\",\"lat\":-47.0,\"lon\":172.0,\"heading\":0,\"speedKmh\":0,\"timestamp\":1}";
        try (KafkaProducer<String, String> p = new KafkaProducer<>(Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            p.send(new ProducerRecord<>("driver-locations", "dlt-poison", badJson));
            p.send(new ProducerRecord<>("driver-locations", "dlt-bad", badCoords));
            p.send(new ProducerRecord<>("driver-locations", "dlt-good", good));
            p.flush();
        }

        List<ConsumerRecord<String, String>> dead = new ArrayList<>();
        try (KafkaConsumer<String, String> c = new KafkaConsumer<>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            c.subscribe(List.of("driver-locations.DLT"));
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                c.poll(Duration.ofMillis(500)).forEach(dead::add);
                return dead.stream().anyMatch(r -> "dlt-poison".equals(r.key()))
                        && dead.stream().anyMatch(r -> "dlt-bad".equals(r.key()));
            });
        }
        ConsumerRecord<String, String> poison = dead.stream().filter(r -> "dlt-poison".equals(r.key())).findFirst().orElseThrow();
        assertThat(poison.value()).isEqualTo(badJson); // raw bytes preserved
        assertThat(poison.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value())
                .isEqualTo("driver-locations".getBytes(StandardCharsets.UTF_8));
        assertThat(poison.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNotNull();
        assertThat(dead).noneMatch(r -> "dlt-good".equals(r.key()));
        assertThat(meters.counter("location.dlt").count()).isGreaterThanOrEqualTo(before + 2);

        // the partition was not blocked by the bad records: the good one behind them is processed
        await().atMost(Duration.ofSeconds(30)).until(() -> index.isIndexed("dlt-good"));
    }
}
