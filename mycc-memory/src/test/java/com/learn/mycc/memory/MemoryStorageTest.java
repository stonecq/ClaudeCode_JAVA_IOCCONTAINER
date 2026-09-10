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

class MemoryStorageTest {

    @TempDir
    Path tempDir;

    FileStorage fileStorage;
    MemoryStorage memory;
    Path projectPath;

    @BeforeEach
    void setUp() {
        projectPath = Path.of("D:", "learn", "mycc");
        fileStorage = new FileStorage(tempDir);
        memory = new MemoryStorage(fileStorage, new ApplicationConfig(projectPath));
    }

    @Test
    void savesAndLoadsUserEntry() {
        memory.save("prefs", "偏好中文回复", "用户偏好中文回复", MemoryType.USER);
        assertThat(memory.load("prefs", MemoryType.USER)).isEqualTo("用户偏好中文回复");
    }

    @Test
    void userEntryLandsAtEncodedFile() {
        memory.save("prefs", "d", "content", MemoryType.USER);
        assertThat(fileStorage.read("memory/user/" + MemoryStorage.encodeProjectPath("prefs") + ".md"))
                .get()
                .asString()
                .contains("content");
    }

    @Test
    void userIndexTracksSavedEntry() {
        memory.save("prefs", "偏好中文回复", "content", MemoryType.USER);
        assertThat(memory.loadIndex(MemoryType.USER)).isEqualTo("- prefs: 偏好中文回复");
    }

    @Test
    void savingSameIdTwiceUpdatesContentAndDescriptionOnly() {
        memory.save("prefs", "旧描述", "content1", MemoryType.USER);
        memory.save("prefs", "新描述", "content2", MemoryType.USER);
        assertThat(memory.loadIndex(MemoryType.USER)).isEqualTo("- prefs: 新描述");
        assertThat(memory.load("prefs", MemoryType.USER)).isEqualTo("content2");
    }

    @Test
    void userIndexListsAllEntries() {
        memory.save("a", "A", "x", MemoryType.USER);
        memory.save("b", "B", "y", MemoryType.USER);
        assertThat(memory.loadIndex(MemoryType.USER)).isEqualTo("- a: A\n- b: B");
    }

    @Test
    void deleteRemovesEntryAndIndexLine() {
        memory.save("a", "A", "x", MemoryType.USER);
        memory.save("b", "B", "y", MemoryType.USER);
        assertThat(memory.delete("a", MemoryType.USER)).isTrue();
        assertThat(memory.load("a", MemoryType.USER)).isNull();
        assertThat(memory.loadIndex(MemoryType.USER)).isEqualTo("- b: B");
    }

    @Test
    void deleteMissingReturnsFalse() {
        assertThat(memory.delete("nope", MemoryType.USER)).isFalse();
    }

    @Test
    void deleteLastEntryRemovesIndexFile() {
        memory.save("a", "A", "x", MemoryType.USER);
        assertThat(memory.delete("a", MemoryType.USER)).isTrue();
        assertThat(memory.load("a", MemoryType.USER)).isNull();
        assertThat(memory.loadIndex(MemoryType.USER)).isNull();
    }

    @Test
    void loadMissingEntryAndIndexReturnNull() {
        assertThat(memory.load("nope", MemoryType.USER)).isNull();
        assertThat(memory.loadIndex(MemoryType.USER)).isNull();
    }

    @Test
    void sessionTypeRejectedByEntryMethods() {
        assertThatThrownBy(() -> memory.save("id", "d", "c", MemoryType.SESSION))
                .isInstanceOf(MyccException.class);
        assertThatThrownBy(() -> memory.load("id", MemoryType.SESSION))
                .isInstanceOf(MyccException.class);
        assertThatThrownBy(() -> memory.loadIndex(MemoryType.SESSION))
                .isInstanceOf(MyccException.class);
        assertThatThrownBy(() -> memory.delete("id", MemoryType.SESSION))
                .isInstanceOf(MyccException.class);
    }

    @Test
    void savesAndLoadsProjectEntry() {
        memory.save("build", "构建命令", "用Maven构建", MemoryType.PROJECT);
        assertThat(memory.load("build", MemoryType.PROJECT)).isEqualTo("用Maven构建");
    }

    @Test
    void projectEntryLandsUnderEncodedWorkspaceNamespace() {
        memory.save("build", "构建命令", "用Maven构建", MemoryType.PROJECT);
        String ns = MemoryStorage.encodeProjectPath(projectPath.toString());
        assertThat(fileStorage.read("memory/project/" + ns + "/" + MemoryStorage.encodeProjectPath("build") + ".md"))
                .get()
                .asString()
                .isEqualTo("用Maven构建");
        assertThat(fileStorage.read("memory/project/" + ns + "/MEMORY.md"))
                .get()
                .asString()
                .contains("build");
    }

    @Test
    void projectMemoryIsolatedPerWorkspace() {
        MemoryStorage other = new MemoryStorage(fileStorage, new ApplicationConfig(Path.of("C:", "other")));
        memory.save("k", "K", "本工作区内容", MemoryType.PROJECT);
        assertThat(other.load("k", MemoryType.PROJECT)).isNull();
        assertThat(other.loadIndex(MemoryType.PROJECT)).isNull();
    }

    @Test
    void savesAndLoadsSessionMemory() {
        memory.saveSession("sess-1", "正在实现M9");
        assertThat(memory.loadSession("sess-1")).isEqualTo("正在实现M9");
    }

    @Test
    void sessionMemoryOverwritesOnResave() {
        memory.saveSession("sess-1", "第一回合");
        memory.saveSession("sess-1", "第二回合");
        assertThat(memory.loadSession("sess-1")).isEqualTo("第二回合");
    }

    @Test
    void sessionMemoryDoesNotTouchIndexes() {
        memory.saveSession("sess-1", "回合记录");
        assertThat(fileStorage.keys())
                .containsExactly("memory/session/sess-1.md");
    }

    @Test
    void encodeDecodeProjectPathRoundTrip() {
        String path = "D:\\learn space\\mycc (1)";
        String encoded = MemoryStorage.encodeProjectPath(path);
        assertThat(MemoryStorage.decodeProjectPath(encoded)).isEqualTo(path);
    }

    @Test
    void entryCountCountsIndexLinesAndZeroWhenEmpty() {
        assertThat(memory.entryCount(MemoryType.USER)).isZero();
        memory.save("a", "A", "x", MemoryType.USER);
        assertThat(memory.entryCount(MemoryType.USER)).isEqualTo(1);
        memory.save("b", "B", "y", MemoryType.USER);
        assertThat(memory.entryCount(MemoryType.USER)).isEqualTo(2);
    }
}