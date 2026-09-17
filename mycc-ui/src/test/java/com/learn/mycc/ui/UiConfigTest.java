package com.learn.mycc.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UiConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsWhenFileMissing() {
        UiConfig config = new UiConfig(tempDir.resolve("none.json"));
        assertThat(config.cliShowReasoning()).isTrue();
        assertThat(config.webPort()).isEqualTo(8080);
    }

    @Test
    void readsUiSection() throws IOException {
        Path file = tempDir.resolve("config.json");
        Files.writeString(file, "{\"ui\":{\"cli\":{\"showReasoning\":false},\"web\":{\"port\":9999}}}",
                StandardCharsets.UTF_8);

        UiConfig config = new UiConfig(file);

        assertThat(config.cliShowReasoning()).isFalse();
        assertThat(config.webPort()).isEqualTo(9999);
    }

    @Test
    void ignoresAgentSection() throws IOException {
        Path file = tempDir.resolve("config.json");
        Files.writeString(file, "{\"agent\":{\"model\":\"x\"}}", StandardCharsets.UTF_8);

        UiConfig config = new UiConfig(file);

        assertThat(config.cliShowReasoning()).isTrue();
        assertThat(config.webPort()).isEqualTo(8080);
    }
}