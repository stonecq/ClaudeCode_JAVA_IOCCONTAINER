package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.RecordingPort;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.hook.HookDefinition;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.hook.HookRegistry;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.memory.MemoryStorage;
import com.learn.mycc.memory.MemoryType;
import com.learn.mycc.memory.hook.MemoryHook;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** M9 记忆注入链路：MemoryHook 经 HookDispatcher 挂进 AgentLoop，记忆文本随 SYSTEM 消息发给 LLM。 */
class AgentMemoryInjectionTest {

    @TempDir
    Path tempDir;

    Path projectPath;
    FileStorage fileStorage;
    MemoryStorage memory;
    MemoryHook memoryHook;
    HookDispatcher dispatcher;
    ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() throws Exception {
        projectPath = Path.of("D:", "learn", "mycc");
        fileStorage = new FileStorage(tempDir);
        memory = new MemoryStorage(fileStorage);
        memoryHook = new MemoryHook(memory, new ApplicationConfig(projectPath));

        HookRegistry registry = new HookRegistry();
        Method hookMethod = MemoryHook.class.getMethod("systemPromptMemoryHook", HookEvent.class);
        registry.register(new HookDefinition(HookEventType.SESSION_START, memoryHook, hookMethod));
        dispatcher = new HookDispatcher(registry);

        toolRegistry = new ToolRegistry();
    }

    @Test
    void injectsSessionMemoryIntoSystemMessage() {
        Session session = Session.create();
        memory.saveMemory("用户正在实现M9", session.id(), MemoryType.SESSION);

        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            captured.set(request);
            return ChatResponse.text("好的，继续。");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 5,
                null, session, dispatcher);

        agent.run("继续");

        List<ChatMessage> messages = captured.get().messages();
        assertThat(messages.get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(messages.get(0).content()).contains("用户正在实现M9");
    }

    @Test
    void noMemoryMeansNoSystemMessage() {
        Session session = Session.create();

        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            captured.set(request);
            return ChatResponse.text("收到");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 5,
                null, session, dispatcher);

        agent.run("你好");

        assertThat(captured.get().messages())
                .noneMatch(m -> m.role() == ChatMessage.Role.SYSTEM);
    }
}