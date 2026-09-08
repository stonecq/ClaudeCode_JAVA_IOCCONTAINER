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
    ApplicationConfig config;
    MemoryStorage memory;
    MemoryHook hook;

    @BeforeEach
    void setUp() {
        projectPath = Path.of("D:", "learn", "mycc");
        config = new ApplicationConfig(projectPath);
        memory = new MemoryStorage(new FileStorage(tempDir));
        hook = new MemoryHook(memory, config);
    }

    @Test
    void appendsAllThreeLayersIntoSystemPromptList() {
        String sessionId = "sess-1";
        memory.saveMemory("会话记忆", sessionId, MemoryType.SESSION);
        memory.saveMemory("项目记忆", config.getWorkspacePath().toString(), MemoryType.PROJECT);
        memory.saveMemory("用户记忆", "mycc_user", MemoryType.USER);

        List<String> systemPromptList = new ArrayList<>(List.of("基础提示"));
        HookEvent event = new HookEvent(HookEventType.SESSION_START, sessionId, systemPromptList);
        hook.systemPromptMemoryHook(event);

        assertThat(systemPromptList).containsExactly(
                "基础提示", "会话记忆", "项目记忆", "用户记忆");
    }

    @Test
    void skipsLayersWithNoMemory() {
        String sessionId = "sess-2";
        List<String> systemPromptList = new ArrayList<>(List.of("基础提示"));
        HookEvent event = new HookEvent(HookEventType.SESSION_START, sessionId, systemPromptList);
        hook.systemPromptMemoryHook(event);

        assertThat(systemPromptList).containsExactly("基础提示");
    }

    @Test
    void doesNothingWhenPayloadNull() {
        HookEvent event = new HookEvent(HookEventType.SESSION_START, "sess-3", null);
        hook.systemPromptMemoryHook(event);
    }
}
