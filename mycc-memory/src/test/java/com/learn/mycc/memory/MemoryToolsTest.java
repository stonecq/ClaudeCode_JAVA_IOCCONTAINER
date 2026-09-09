package com.learn.mycc.memory;

import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryToolsTest {

    @TempDir
    Path tempDir;

    MemoryStorage storage;
    MemoryTools tools;

    @BeforeEach
    void setUp() {
        storage = new MemoryStorage(new FileStorage(tempDir), new ApplicationConfig(Path.of("D:", "learn", "mycc")));
        tools = new MemoryTools(storage);
    }

    @Test
    void readReturnsEntryContent() {
        storage.save("prefs", "偏好中文回复", "喜欢中文回复", MemoryType.USER);
        assertThat(tools.readMemory(MemoryType.USER, "prefs")).isEqualTo("喜欢中文回复");
    }

    @Test
    void readMissingReturnsNotFound() {
        assertThat(tools.readMemory(MemoryType.USER, "nope")).isEqualTo("未找到记忆: nope");
    }

    @Test
    void saveAddsEntryWithAcknowledgement() {
        String result = tools.saveMemory(MemoryType.PROJECT, "build-notes", "常用构建命令", "mvn clean install");
        assertThat(result).isEqualTo("已保存: build-notes");
        assertThat(storage.load("build-notes", MemoryType.PROJECT)).isEqualTo("mvn clean install");
        assertThat(storage.loadIndex(MemoryType.PROJECT)).contains("build-notes: 常用构建命令");
    }

    @Test
    void saveSameIdOverwrites() {
        tools.saveMemory(MemoryType.USER, "prefs", "旧", "v1");
        tools.saveMemory(MemoryType.USER, "prefs", "新", "v2");
        assertThat(storage.load("prefs", MemoryType.USER)).isEqualTo("v2");
        assertThat(storage.loadIndex(MemoryType.USER)).isEqualTo("- prefs: 新");
    }

    @Test
    void deleteExistingReturnsOkAndRemovesEntry() {
        storage.save("prefs", "偏好", "内容", MemoryType.USER);
        assertThat(tools.deleteMemory(MemoryType.USER, "prefs")).isEqualTo("已删除: prefs");
        assertThat(storage.load("prefs", MemoryType.USER)).isNull();
    }

    @Test
    void deleteMissingReturnsNotFound() {
        assertThat(tools.deleteMemory(MemoryType.USER, "nope")).isEqualTo("未找到记忆: nope");
    }

    @Test
    void toolsRejectSessionLayer() {
        assertThatThrownBy(() -> tools.readMemory(MemoryType.SESSION, "s"))
                .isInstanceOf(MyccException.class);
        assertThatThrownBy(() -> tools.saveMemory(MemoryType.SESSION, "s", "d", "c"))
                .isInstanceOf(MyccException.class);
        assertThatThrownBy(() -> tools.deleteMemory(MemoryType.SESSION, "s"))
                .isInstanceOf(MyccException.class);
    }
}