package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.RecordingPort;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new Tools(), "tools");
    }

    @Test
    void streamsFullEventSequenceForToolTurnThenFinalText() {
        MockProvider provider = MockProvider.scripted(request -> {
            boolean hasToolResult = request.messages().stream()
                    .anyMatch(m -> m.role() == ChatMessage.Role.TOOL);
            if (!hasToolResult) {
                return new ChatResponse("", List.of(new ToolCall("call_1", "echo", "{\"text\":\"hi\"}")));
            }
            return ChatResponse.text("done");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, registry, "mock", 10);

        String result = agent.run("say hi");

        assertThat(result).isEqualTo("done");
        assertThat(port.types()).containsExactly(
                OutputEventType.TOOL_CALL,
                OutputEventType.TOOL_RESULT,
                OutputEventType.TOKEN,
                OutputEventType.DONE);
        assertThat(port.events).extracting(OutputEvent::seq).containsExactly(0L, 1L, 2L, 3L);
        assertThat(port.events).allSatisfy(event -> assertThat(event.sessionId()).isEqualTo(agent.session().id()));
    }

    @Test
    void feedsToolFailureBackToLlm() {
        MockProvider provider = MockProvider.scripted(request -> {
            long toolCount = request.messages().stream()
                    .filter(m -> m.role() == ChatMessage.Role.TOOL).count();
            if (toolCount == 0) {
                return new ChatResponse("", List.of(new ToolCall("c1", "boom", "{}")));
            }
            String toolContent = request.messages().stream()
                    .filter(m -> m.role() == ChatMessage.Role.TOOL)
                    .map(ChatMessage::content)
                    .reduce((first, second) -> second)
                    .orElse("");
            return ChatResponse.text("工具报错：" + toolContent);
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, registry, "mock", 10);

        String result = agent.run("boom");

        assertThat(result).contains("kaboom");
        assertThat(port.types()).contains(OutputEventType.TOOL_RESULT);
        assertThat(agent.session().conversation().messages()).extracting(Message::role)
                .contains(ChatMessage.Role.TOOL);
    }

    @Test
    void stopsWhenMaxIterationsReached() {
        MockProvider provider = MockProvider.scripted(request ->
                new ChatResponse("", List.of(new ToolCall("c1", "boom", "{}"))));
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, registry, "mock", 3);

        String result = agent.run("loop");

        assertThat(result).contains("最大迭代次数");
        assertThat(port.types()).last().isEqualTo(OutputEventType.DONE);
    }

    @Test
    void surfacesProviderErrorAsErrorEvent() {
        MockProvider provider = MockProvider.scripted(request -> {
            throw new IllegalStateException("provider-down");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, registry, "mock", 10);

        String result = agent.run("hi");

        assertThat(result).contains("provider-down");
        assertThat(port.types()).containsExactly(OutputEventType.ERROR);
    }

    public static final class Tools {

        @Tool(name = "echo", description = "回显")
        public String echo(String text) {
            return "echo:" + text;
        }

        @Tool(name = "boom", description = "抛异常")
        public String boom() {
            throw new IllegalStateException("kaboom");
        }
    }
}
