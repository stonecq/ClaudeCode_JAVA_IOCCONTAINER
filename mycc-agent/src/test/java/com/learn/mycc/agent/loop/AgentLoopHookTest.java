package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.RecordingPort;
import com.learn.mycc.agent.loop.fixture.DenyEchoHook;
import com.learn.mycc.agent.loop.fixture.HookRecorder;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.core.hook.HookRegistry;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.ui.OutputEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopHookTest {

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.postProcessAfterInitialization(new Tools(), "tools");
        HookRecorder.clear();
    }

    private HookDispatcher hooks() {
        HookRegistry hookRegistry = new HookRegistry();
        hookRegistry.postProcessAfterInitialization(new HookRecorder(), "recorder");
        return new HookDispatcher(hookRegistry);
    }

    @Test
    void dispatchesHookSequenceForToolTurn() {
        MockProvider provider = MockProvider.scripted(request -> {
            boolean hasToolResult = request.messages().stream()
                    .anyMatch(m -> m.role() == ChatMessage.Role.TOOL);
            if (!hasToolResult) {
                return new ChatResponse("", List.of(new ToolCall("call_1", "echo", "{\"text\":\"hi\"}")));
            }
            return ChatResponse.text("done");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 10,
                null, Session.create(), hooks());

        agent.run("hi");

        assertThat(HookRecorder.EVENTS).containsExactly(
                "session_start",
                "user_prompt_submit",
                "tool_call_before",
                "tool_call_after",
                "session_end");
    }

    @Test
    void skipsSessionStartForResumedSession() {
        MockProvider provider = MockProvider.scripted(request -> ChatResponse.text("done"));
        RecordingPort port = new RecordingPort();
        // 模拟从存储恢复的历史会话：已含消息，非新建，不应触发 session_start。
        Session resumed = Session.create();
        resumed.addMessage(Message.user("早前的问题"));
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 10,
                null, resumed, hooks());

        agent.run("hi");

        assertThat(HookRecorder.EVENTS).containsExactly(
                "user_prompt_submit",
                "session_end");
    }

    @Test
    void dispatchesErrorHookOnProviderFailure() {
        MockProvider provider = MockProvider.scripted(request -> {
            throw new IllegalStateException("provider-down");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 10,
                null, Session.create(), hooks());

        agent.run("hi");

        assertThat(HookRecorder.EVENTS).contains("error");
        assertThat(HookRecorder.EVENTS).first().isEqualTo("session_start");
        assertThat(HookRecorder.EVENTS).last().isEqualTo("session_end");
    }

    @Test
    void deniesToolCallBeforeExecution() {
        MockProvider provider = MockProvider.scripted(request -> {
            boolean hasToolResult = request.messages().stream()
                    .anyMatch(m -> m.role() == ChatMessage.Role.TOOL);
            if (!hasToolResult) {
                return new ChatResponse("", List.of(new ToolCall("call_1", "echo", "{\"text\":\"hi\"}")));
            }
            return ChatResponse.text("done");
        });
        HookRegistry hookRegistry = new HookRegistry();
        hookRegistry.postProcessAfterInitialization(new HookRecorder(), "recorder");
        hookRegistry.postProcessAfterInitialization(new DenyEchoHook(), "denyEcho");
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 10,
                null, Session.create(), new HookDispatcher(hookRegistry));

        String finalText = agent.run("hi");

        // 否决后：工具未执行，不派发 tool_call_after；拦截原因作为 tool 结果回填给 LLM。
        assertThat(finalText).isEqualTo("done");
        assertThat(HookRecorder.EVENTS).contains("tool_call_before");
        assertThat(HookRecorder.EVENTS).doesNotContain("tool_call_after");
        assertThat(port.events).filteredOn(e -> e.type() == OutputEventType.TOOL_RESULT)
                .anySatisfy(e -> assertThat(e.payload()).contains("被钩子拦截: " + DenyEchoHook.REASON));
        assertThat(agent.session().conversation().toChatMessages())
                .anySatisfy(m -> {
                    assertThat(m.role()).isEqualTo(ChatMessage.Role.TOOL);
                    assertThat(m.content()).isEqualTo("被钩子拦截: " + DenyEchoHook.REASON);
                });
    }

    public static final class Tools {

        @Tool(name = "echo", description = "回显")
        public String echo(String text) {
            return "echo:" + text;
        }
    }
}
