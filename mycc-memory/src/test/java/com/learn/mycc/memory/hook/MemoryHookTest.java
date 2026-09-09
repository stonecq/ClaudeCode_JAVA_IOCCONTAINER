package com.learn.mycc.memory.hook;

import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.memory.MemoryStorage;
import com.learn.mycc.memory.MemoryType;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryHookTest {

    @TempDir
    Path tempDir;

    Path projectPath;
    MemoryStorage memory;
    MemoryHook hook;

    @BeforeEach
    void setUp() {
        projectPath = Path.of("D:", "learn", "mycc");
        memory = new MemoryStorage(new FileStorage(tempDir), new ApplicationConfig(projectPath));
        hook = new MemoryHook(memory);
    }

    @Test
    void injectsSessionFullTextAndUserAndProjectIndexesIntoSystemPromptList() {
        String sessionId = "sess-1";
        memory.saveSession(sessionId, "正在实现M9");
        memory.save("prefs", "偏好中文回复", "用户偏好中文", MemoryType.USER);
        memory.save("build", "构建命令", "用Maven构建", MemoryType.PROJECT);

        List<String> systemPromptList = new ArrayList<>(List.of("基础提示"));
        hook.memoryIndexHook(new HookEvent(HookEventType.SESSION_START, sessionId, systemPromptList));

        assertThat(systemPromptList).containsExactly(
                "基础提示",
                "【会话记忆】\n正在实现M9",
                "【用户长期记忆索引】可用 read_memory 按 id 调取全文：\n- prefs: 偏好中文回复",
                "【项目长期记忆索引】可用 read_memory 按 id 调取全文：\n- build: 构建命令");
    }

    @Test
    void skipsLayersWithNoMemory() {
        String sessionId = "sess-2";
        List<String> systemPromptList = new ArrayList<>(List.of("基础提示"));
        hook.memoryIndexHook(new HookEvent(HookEventType.SESSION_START, sessionId, systemPromptList));

        assertThat(systemPromptList).containsExactly("基础提示");
    }

    @Test
    void doesNothingWhenPayloadNull() {
        hook.memoryIndexHook(new HookEvent(HookEventType.SESSION_START, "sess-3", null));
    }

    @Test
    void sessionEndSavesTurnIntoSessionMemory() {
        hook.saveTurnMemoryHook(new HookEvent(HookEventType.SESSION_END, "sess-1",
                "用户: 继续实现M9\n回答: 好的，继续。"));
        assertThat(memory.loadSession("sess-1")).isEqualTo("用户: 继续实现M9\n回答: 好的，继续。");
    }

    @Test
    void sessionEndIgnoresNonStringPayload() {
        hook.saveTurnMemoryHook(new HookEvent(HookEventType.SESSION_END, "sess-1", 123));
        assertThat(memory.loadSession("sess-1")).isNull();
    }

    @Test
    void sessionEndIgnoresNullPayload() {
        hook.saveTurnMemoryHook(new HookEvent(HookEventType.SESSION_END, "sess-1", null));
        assertThat(memory.loadSession("sess-1")).isNull();
    }
}