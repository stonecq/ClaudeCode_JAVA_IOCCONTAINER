package com.learn.mycc.memory;

import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStorageTest {

    @TempDir
    Path tempDir;

    MemoryStorage memory;

    @BeforeEach
    void setUp() {
        memory = new MemoryStorage(new FileStorage(tempDir));
    }

    @Test
    void savesAndLoadsUserMemory() {
        memory.saveMemory("偏好中文", "mycc_user", MemoryType.USER);
        assertThat(memory.loadMemory("mycc_user", MemoryType.USER)).isEqualTo("偏好中文");
    }

    @Test
    void savesAndLoadsSessionMemory() {
        memory.saveMemory("正在实现M9", "sess-1", MemoryType.SESSION);
        assertThat(memory.loadMemory("sess-1", MemoryType.SESSION)).isEqualTo("正在实现M9");
    }

    @Test
    void savesAndLoadsProjectMemory() {
        memory.saveMemory("用Maven构建", "D:\\learn\\mycc", MemoryType.PROJECT);
        assertThat(memory.loadMemory("D:\\learn\\mycc", MemoryType.PROJECT)).isEqualTo("用Maven构建");
    }

    @Test
    void threeLayersDoNotCollideWithSameId() {
        memory.saveMemory("user内容", "same-id", MemoryType.USER);
        memory.saveMemory("session内容", "same-id", MemoryType.SESSION);
        memory.saveMemory("project内容", "same-id", MemoryType.PROJECT);

        assertThat(memory.loadMemory("same-id", MemoryType.USER)).isEqualTo("user内容");
        assertThat(memory.loadMemory("same-id", MemoryType.SESSION)).isEqualTo("session内容");
        assertThat(memory.loadMemory("same-id", MemoryType.PROJECT)).isEqualTo("project内容");
    }

    @Test
    void loadMissingReturnsNull() {
        assertThat(memory.loadMemory("not-exists", MemoryType.USER)).isNull();
    }

    @Test
    void projectKeyUrlEncodesPath() {
        String path = "D:\\a\\b";
        memory.saveMemory("x", path, MemoryType.PROJECT);
        // project 层把路径 URL 编码后落到 memory/project/<encoded>.json
        assertThat(new FileStorage(tempDir).read("memory/project/" + MemoryStorage.encodeProjectPath(path) + ".json"))
                .contains("x");
    }

    @Test
    void encodeDecodeProjectPathRoundTrip() {
        String path = "D:\\learn space\\mycc (1)";
        String encoded = MemoryStorage.encodeProjectPath(path);
        assertThat(MemoryStorage.decodeProjectPath(encoded)).isEqualTo(path);
    }

    @Test
    void userAndSessionKeysUseRawId() {
        memory.saveMemory("u", "abc", MemoryType.USER);
        memory.saveMemory("s", "def", MemoryType.SESSION);
        assertThat(new FileStorage(tempDir).read("memory/user/abc.json")).contains("u");
        assertThat(new FileStorage(tempDir).read("memory/session/def.json")).contains("s");
    }
}
