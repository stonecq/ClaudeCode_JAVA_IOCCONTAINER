package com.learn.mycc.tools;

import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileToolsTest {

    @TempDir
    Path workspace;

    private FileTools tool() {
        return new FileTools(new ApplicationConfig(workspace));
    }

    @Test
    void writeThenReadRoundTrip() {
        FileTools tool = tool();
        assertThat(tool.writeFile("notes/a.txt", "hello")).contains("已写入");
        assertThat(tool.readFile("notes/a.txt")).isEqualTo("hello");
        assertThat(workspace.resolve("notes/a.txt")).exists();
    }

    @Test
    void editFileReplacesFirstOccurrence() throws IOException {
        Files.writeString(workspace.resolve("f.txt"), "abc abc");
        assertThat(tool().editFile("f.txt", "abc", "x")).contains("已编辑");
        assertThat(Files.readString(workspace.resolve("f.txt"))).isEqualTo("x abc");
    }

    @Test
    void editFileThrowsWhenOldTextMissing() throws IOException {
        Files.writeString(workspace.resolve("f.txt"), "hello");
        assertThatThrownBy(() -> tool().editFile("f.txt", "nope", "x"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("未找到");
    }

    @Test
    void readFileMissingThrows() {
        assertThatThrownBy(() -> tool().readFile("missing.txt"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("不存在");
    }
}
