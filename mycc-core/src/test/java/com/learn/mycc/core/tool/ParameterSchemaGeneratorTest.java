package com.learn.mycc.core.tool;

import com.learn.mycc.core.tool.fixture.SchemaFixture;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterSchemaGeneratorTest {

    private final ParameterSchemaGenerator generator = new ParameterSchemaGenerator();

    @Test
    void generatesSchemaForBasicTypesAndEnum() throws Exception {
        Method method = SchemaFixture.class.getMethod("mixed", String.class, int.class, boolean.class, SchemaFixture.Mode.class);
        Map<String, Object> schema = generator.generate(method);

        assertThat(schema).containsEntry("type", "object");
        Map<String, Object> properties = propertiesOf(schema);
        assertThat(properties).containsKeys("path", "count", "verbose", "mode");

        assertThat(property(properties, "path"))
                .containsEntry("type", "string")
                .containsEntry("description", "路径");
        assertThat(property(properties, "count")).containsEntry("type", "integer");
        assertThat(property(properties, "verbose")).containsEntry("type", "boolean");
        assertThat(property(properties, "mode"))
                .containsEntry("type", "string")
                .containsEntry("enum", List.of("FAST", "SAFE"));
    }

    @Test
    void requiredIncludesAnnotatedAndDefaultsExcludingOptional() throws Exception {
        Method method = SchemaFixture.class.getMethod("mixed", String.class, int.class, boolean.class, SchemaFixture.Mode.class);
        Map<String, Object> schema = generator.generate(method);
        assertThat(schema.get("required")).isEqualTo(List.of("path", "count", "mode"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> propertiesOf(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> property(Map<String, Object> properties, String name) {
        return (Map<String, Object>) properties.get(name);
    }
}
