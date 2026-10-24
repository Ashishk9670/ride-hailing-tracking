package com.example.ridehailing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ridehailing.domain.TripStatus;
import com.example.ridehailing.dto.LocationUpdate;
import com.example.ridehailing.dto.TripEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.JacksonUtils;

/** Producer-side contract: what we serialize for Kafka/WebSocket must satisfy the published schemas in docs/schemas. */
class EventSchemaContractTest {
    // same mapper family Spring Kafka's JsonSerializer uses
    private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();

    private JsonSchema schema(String name) throws IOException {
        try (InputStream in = Files.newInputStream(Path.of("docs/schemas", name))) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in);
        }
    }

    private boolean valid(String schemaName, Object payload) throws IOException {
        JsonNode node = mapper.valueToTree(payload);
        return schema(schemaName).validate(node).isEmpty();
    }

    private boolean validJson(String schemaName, String json) throws IOException {
        return schema(schemaName).validate(mapper.readTree(json)).isEmpty();
    }

    @Test
    void locationUpdateMatchesSchema() throws IOException {
        var u = new LocationUpdate("d-1", null, 12.97, 77.59, 90.0, 32.5, System.currentTimeMillis());
        assertThat(valid("driver-location.schema.json", u)).isTrue();
        assertThat(valid("driver-location.schema.json", u.withTripId("t-1"))).isTrue();
    }

    @Test
    void locationSchemaRejectsOutOfRangeAndUnknownFields() throws IOException {
        assertThat(validJson("driver-location.schema.json",
                "{\"driverId\":\"d\",\"lat\":91,\"lon\":0,\"heading\":0,\"speedKmh\":0,\"timestamp\":1}")).isFalse();
        assertThat(validJson("driver-location.schema.json",
                "{\"driverId\":\"d\",\"lat\":1,\"lon\":0,\"heading\":0,\"speedKmh\":0,\"timestamp\":1,\"extra\":1}")).isFalse();
        assertThat(validJson("driver-location.schema.json", "{\"lat\":1,\"lon\":0}")).isFalse();
    }

    @Test
    void everyTripStatusProducesValidTripEvent() throws IOException {
        for (TripStatus s : TripStatus.values()) {
            assertThat(valid("trip-event.schema.json", new TripEvent("STATUS", "t-1", "d-1", s, 1L))).isTrue();
            assertThat(valid("trip-event.schema.json", new TripEvent("STATUS", "t-1", null, s, 1L, 120))).isTrue();
        }
    }

    @Test
    void tripEventSchemaRejectsUnknownStatus() throws IOException {
        assertThat(validJson("trip-event.schema.json",
                "{\"type\":\"STATUS\",\"tripId\":\"t\",\"status\":\"FLYING\",\"timestamp\":1}")).isFalse();
    }

    @Test
    void tripStatusEnumInSchemaMatchesJavaEnum() throws IOException {
        JsonNode values = mapper.readTree(Files.readString(Path.of("docs/schemas/trip-event.schema.json")))
                .at("/properties/status/enum");
        var fromSchema = new java.util.TreeSet<String>();
        values.forEach(v -> { if (!v.isNull()) fromSchema.add(v.asText()); });
        var fromJava = new java.util.TreeSet<String>();
        for (TripStatus s : TripStatus.values()) fromJava.add(s.name());
        assertThat(fromSchema).isEqualTo(fromJava);
    }
}
