package com.learn.mycc.storage.file;

import com.learn.mycc.core.exception.MyccException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageTest {

    @TempDir
    Path tempDir;

    FileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorage(tempDir);
    }

    @Test
    void writesAndReadsContent() {
        storage.write("a.txt", "hello");
        assertThat(storage.read("a.txt")).contains("hello");
    }

    @Test
    void overwritesExistingKey() {
        storage.write("a.txt", "first");
        storage.write("a.txt", "second");
        assertThat(storage.read("a.txt")).contains("second");
    }

    @Test
    void createsNestedDirectoriesForSlashKey() {
        storage.write("session/abc.json", "{}");
        assertThat(Files.isRegularFile(tempDir.resolve("session/abc.json"))).isTrue();
    }

    @Test
    void readsMissingKeyAsEmpty() {
        assertThat(storage.read("missing.txt")).isEmpty();
    }

    @Test
    void deletesExistingKey() {
        storage.write("a.txt", "hello");
        assertThat(storage.delete("a.txt")).isTrue();
        assertThat(storage.read("a.txt")).isEmpty();
    }

    @Test
    void deleteMissingKeyReturnsFalse() {
        assertThat(storage.delete("missing.txt")).isFalse();
    }

    @Test
    void listsKeysRelativeAndSorted() {
        storage.write("b.txt", "1");
        storage.write("session/c.json", "2");
        storage.write("a.txt", "3");
        assertThat(storage.keys()).containsExactly("a.txt", "b.txt", "session/c.json");
    }

    @Test
    void listsKeysEmptyForFreshRoot() {
        assertThat(storage.keys()).isEmpty();
    }

    @Test
    void rejectsEmptyKey() {
        assertThatThrownBy(() -> storage.write("", "x"))
                .isInstanceOf(MyccException.class);
    }

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> storage.write("../evil.txt", "x"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("非法存储路径");
    }

    @Test
    void rejectsDeepPathTraversal() {
        assertThatThrownBy(() -> storage.write("session/../../evil.txt", "x"))
                .isInstanceOf(MyccException.class);
    }

    @Test
    void rejectsAbsolutePathOutsideRoot() {
        String outside = tempDir.getParent().resolve("outside.txt").toString();
        assertThatThrownBy(() -> storage.write(outside, "x"))
                .isInstanceOf(MyccException.class);
    }

    @Test
    void rejectsNulByte() {
        assertThatThrownBy(() -> storage.write("a\0b.txt", "x"))
                .isInstanceOf(MyccException.class);
    }
}
