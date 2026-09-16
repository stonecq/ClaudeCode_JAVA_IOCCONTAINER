package com.learn.mycc.storage.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    private Path configFile() {
        return tempDir.resolve("config.json");
    }

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("mycc.agent.model");
    }

    @Test
    void generatesDefaultConfigFileWhenMissing() throws IOException {
        new ConfigService(configFile());

        assertThat(Files.isRegularFile(configFile())).isTrue();
        assertThat(Files.readString(configFile(), StandardCharsets.UTF_8))
                .contains("\"agent\"").contains("deepseek-v4-flash").contains("\"compact\"");
    }

    @Test
    void usesBuiltinDefaultWhenNothingConfigured() {
        ConfigService config = new ConfigService(configFile());
        assertThat(config.get(ConfigDefaults.AGENT_MODEL)).contains("deepseek-v4-flash");
        assertThat(config.getInt(ConfigDefaults.AGENT_MAX_ITERATIONS)).isEqualTo(10);
        assertThat(config.getBool(ConfigDefaults.CLI_SHOW_REASONING)).isTrue();
    }

    @Test
    void unregisteredKeyReturnsEmpty() {
        ConfigService config = new ConfigService(configFile());
        assertThat(config.get("nope.nothing")).isEmpty();
    }

    @Test
    void loadsNestedConfigFileFlattenedToDottedKey() throws IOException {
        Files.writeString(configFile(), "{\"agent\":{\"model\":\"file-model\"}}", StandardCharsets.UTF_8);
        ConfigService config = new ConfigService(configFile());
        assertThat(config.get(ConfigDefaults.AGENT_MODEL)).contains("file-model");
    }

    @Test
    void configFileOverridesBuiltinDefault() throws IOException {
        Files.writeString(configFile(), "{\"compact\":{\"maxMessages\":7}}", StandardCharsets.UTF_8);
        ConfigService config = new ConfigService(configFile());
        assertThat(config.getInt(ConfigDefaults.COMPACT_MAX_MESSAGES)).isEqualTo(7);
    }

    @Test
    void systemPropertyOverridesConfigFile() throws IOException {
        Files.writeString(configFile(), "{\"agent\":{\"model\":\"file-model\"}}", StandardCharsets.UTF_8);
        System.setProperty("mycc.agent.model", "sys-model");
        ConfigService config = new ConfigService(configFile());
        assertThat(config.get(ConfigDefaults.AGENT_MODEL)).contains("sys-model");
    }

    @Test
    void envNameMapsDotsAndCamelCaseToSnakeUpper() {
        assertThat(ConfigService.envName("agent.maxIterations")).isEqualTo("MYCC_AGENT_MAX_ITERATIONS");
        assertThat(ConfigService.envName("compact.resultBudget")).isEqualTo("MYCC_COMPACT_RESULT_BUDGET");
    }

    @Test
    void workpathDefaultsToCurrentDirMarker() {
        ConfigService config = new ConfigService(configFile());
        assertThat(config.get(ConfigDefaults.WORKSPACE_PATH)).contains(".");
    }
}