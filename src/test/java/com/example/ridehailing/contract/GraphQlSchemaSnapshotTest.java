package com.example.ridehailing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Snapshot of the public GraphQL contract. A failing diff means the API changed: review it for breaking changes,
 * then regenerate with {@code mvn test -Dtest=GraphQlSchemaSnapshotTest -Dupdate.snapshot=true}.
 */
class GraphQlSchemaSnapshotTest {
    private static final Path SNAPSHOT = Path.of("src/test/resources/contract/schema.snapshot.graphqls");

    @Test
    void schemaMatchesSnapshot() throws IOException {
        var registry = new SchemaParser().parse(Path.of("src/main/resources/graphql/schema.graphqls").toFile());
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
        String printed = new SchemaPrinter(SchemaPrinter.Options.defaultOptions()
                .includeDirectives(false).includeScalarTypes(false)).print(schema).replace("\r\n", "\n");
        if (Boolean.getBoolean("update.snapshot")) {
            Files.writeString(SNAPSHOT, printed);
        }
        assertThat(printed).isEqualTo(Files.readString(SNAPSHOT));
    }
}
