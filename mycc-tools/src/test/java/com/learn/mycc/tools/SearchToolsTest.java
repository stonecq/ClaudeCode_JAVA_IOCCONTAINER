package com.learn.mycc.tools;

import com.learn.mycc.core.exception.MyccException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchToolsTest {

    @TempDir
    Path workspace;

    @BeforeEach
    void createFiles() throws IOException {
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/a.java"), "class A {}\n");
        Files.writeString(workspace.resolve("src/b.txt"), "hello world\n");
        Files.writeString(workspace.resolve("README.md"), "Hello, foo\n");
    }

    private SearchTools tool() {
        return new SearchTools(workspace);
    }

    @Test
    void globMatchesRecursivePattern() {
        assertThat(tool().glob("**/*.java")).contains("src/a.java");
    }

    @Test
    void globNoMatchReturnsMessage() {
        assertThat(tool().glob("**/*.xyz")).isEqualTo("无匹配结果");
    }

    @Test
    void globRejectsBlankPattern() {
        assertThatThrownBy(() -> tool().glob(" "))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void grepMatchesLinesWithLineNumber() {
        assertThat(tool().grep("hello", null)).contains("src/b.txt:1: hello world");
    }

    @Test
    void grepRespectsCaseSensitivity() {
        assertThat(tool().grep("HELLO", null)).isEqualTo("无匹配结果");
    }

    @Test
    void searchFilesFindsFileContainingQuery() {
        assertThat(tool().searchFiles("world", null)).contains("src/b.txt");
    }

    @Test
    void searchFilesNoMatch() {
        assertThat(tool().searchFiles("zzz", null)).isEqualTo("无匹配结果");
    }

    @Test
    void searchScopesToGivenDirectory() {
        assertThat(tool().searchFiles("foo", "README.md")).contains("README.md");
        assertThat(tool().searchFiles("foo", "README.md")).doesNotContain("src/b.txt");
    }

    @Test
    void rejectsTraversalPath() {
        assertThatThrownBy(() -> tool().grep("x", "../.."))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("超出工作区");
    }

    @Test
    void rejectsMissingPath() {
        assertThatThrownBy(() -> tool().grep("x", "nope"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("不存在");
    }
}
