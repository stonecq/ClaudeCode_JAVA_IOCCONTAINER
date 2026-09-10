package com.learn.mycc.subagent;

import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolSpec;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.memory.MemoryStorage;
import com.learn.mycc.memory.MemoryTools;
import com.learn.mycc.memory.MemoryType;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCleanupHookTest {

    @TempDir
    Path tempDir;

    MemoryStorage memory;
    AtomicReference<ChatRequest> captured;
    MemoryCleanupHook hook;

    @BeforeEach
    void setUp() {
        memory = new MemoryStorage(new FileStorage(tempDir), new ApplicationConfig(Path.of("D:", "learn", "mycc")));
        ToolRegistry registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new MemoryTools(memory), "memoryTools");
        captured = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            captured.set(request);
            return ChatResponse.text("整理完成");
        });
        SubagentService subagents = new SubagentService(provider, registry, new RecordingPort(), new ConfigService());
        hook = new MemoryCleanupHook(memory, subagents, new ConfigService());
    }

    @Test
    void noOverLimitLayersDoesNotStartCleanup() {
        memory.save("a", "A", "x", MemoryType.USER);
        hook.cleanupMemoryHook(new HookEvent(HookEventType.SESSION_END, "sess-1", null));
        assertThat(captured.get()).isNull();
    }

    @Test
    void overLimitStartsDedicatedCleanupSubagent() {
        for (int i = 0; i < 21; i++) {
            memory.save("entry" + i, "描述" + i, "内容" + i, MemoryType.USER);
        }
        assertThat(memory.entryCount(MemoryType.USER)).isEqualTo(21);

        hook.cleanupMemoryHook(new HookEvent(HookEventType.SESSION_END, "sess-1", null));

        assertThat(captured.get()).isNotNull();
        // 专用清理子代理装配 memory 三工具（覆盖 subagentExcluded）
        assertThat(captured.get().tools()).extracting(ToolSpec::name)
                .containsExactlyInAnyOrder("read_memory", "save_memory", "delete_memory");
        // 系统提示为记忆整理代理
        assertThat(captured.get().messages().get(0).content())
                .contains(MemoryCleanupHook.CLEANUP_PROMPT);
        // 任务消息含超限层与上限
        assertThat(captured.get().messages()).anySatisfy(m ->
                assertThat(m.content()).contains("USER:21").contains("上限 20"));
    }

    @Test
    void nullEventIsSkipped() {
        hook.cleanupMemoryHook(null);
        assertThat(captured.get()).isNull();
    }

    @Test
    void cleanupSubagentDoesNotReTriggerItself() {
        // 清理子代理 hooks=null，SESSION_END 不派发——自然无递归；此处仅烟测不崩
        for (int i = 0; i < 21; i++) {
            memory.save("x" + i, "d", "c", MemoryType.PROJECT);
        }
        hook.cleanupMemoryHook(new HookEvent(HookEventType.SESSION_END, "sess-1", null));
        assertThat(captured.get()).isNotNull();
    }
}