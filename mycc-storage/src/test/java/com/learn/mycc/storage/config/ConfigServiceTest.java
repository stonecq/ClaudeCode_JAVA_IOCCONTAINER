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

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("mycc.model");
    }

    @Test
    void usesDefaultWhenNothingConfigured() {
        ConfigService config = new ConfigService(tempDir.resolve("config"));
        assertThat(config.get("model", "deepseek-v4-flash")).isEqualTo("deepseek-v4-flash");
    }

    @Test
    void missingKeyReturnsEmpty() {
        ConfigService config = new ConfigService(tempDir.resolve("config"));
        assertThat(config.get("model")).isEmpty();
    }

    @Test
    void loadsValueFromConfigFile() throws IOException {
        writeConfig("model=file-model\nbase-url=https://api.example.com\n");
        ConfigService config = new ConfigService(tempDir.resolve("config"));
        assertThat(config.get("model")).contains("file-model");
        assertThat(config.get("base-url")).contains("https://api.example.com");
    }

    @Test
    void configFileOverridesDefault() throws IOException {
        writeConfig("model=file-model\n");
        ConfigService config = new ConfigService(tempDir.resolve("config"));
        assertThat(config.get("model", "default")).isEqualTo("file-model");
    }

    @Test
    void systemPropertyOverridesConfigFile() throws IOException {
        writeConfig("model=file-model\n");
        System.setProperty("mycc.model", "sys-model");
        ConfigService config = new ConfigService(tempDir.resolve("config"));
        assertThat(config.get("model")).contains("sys-model");
    }

    @Test
    void ignoresMissingConfigFile() {
        ConfigService config = new ConfigService(tempDir.resolve("nonexistent-config"));
        assertThat(config.get("model", "fallback")).isEqualTo("fallback");
    }

    private void writeConfig(String content) throws IOException {
        Files.writeString(tempDir.resolve("config"), content, StandardCharsets.UTF_8);
    }
}
