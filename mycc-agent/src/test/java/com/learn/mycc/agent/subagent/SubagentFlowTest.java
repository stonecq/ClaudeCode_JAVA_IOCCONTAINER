package com.learn.mycc.agent.subagent;

import com.learn.mycc.agent.RecordingPort;
import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.model.ToolSpec;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** M12 子代理链路端到端：主代理调 subagent → 子代理独立运行 → 结果回填主会话，输出复用主 port。 */
class SubagentFlowTest {

    @Test
    void subagentRunsNestedLoopAndReturnsResultToMainAgent() {
        RecordingPort port = new RecordingPort();
        ToolRegistry registry = new ToolRegistry();
        Session mainSession = Session.create();

        // 主/子代理共享同一 provider：按 conversationId 区分主请求（mainSession.id）与子请求（独立子会话）
        AtomicReference<ChatRequest> mainFirstRequest = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            if (request.conversationId().equals(mainSession.id())) {
                if (mainFirstRequest.get() == null) {
                    mainFirstRequest.set(request);
                }
                boolean hasSubResult = request.messages().stream()
                        .anyMatch(m -> m.role() == ChatMessage.Role.TOOL && m.content().contains("子结果："));
                if (!hasSubResult) {
                    return new ChatResponse("",
                            List.of(new ToolCall("m1", "subagent", "{\"task\":\"找到项目核心类\"}")));
                }
                return ChatResponse.text("主代理完成");
            }
            // 子代理请求：独立会话上下文，直接给出文本结论
            return ChatResponse.text("子结果：核心类是 Main");
        });

        SubagentService service = new SubagentService(provider, registry, port, new ConfigService());
        registry.postProcessAfterInitialization(new SubagentTools(service), "subagentTools");

        AgentLoop main = AgentLoop.withToolRegistry(port, provider, registry, "mock", 10,
                null, mainSession, null);

        String finalText = main.run("把一个子任务分派给子代理");

        assertThat(finalText).isEqualTo("主代理完成");
        // subagent 工具出现在主代理声明的工具列表
        assertThat(mainFirstRequest.get().tools()).extracting(ToolSpec::name).contains("subagent");
        // 子代理结果回填到主会话历史
        assertThat(main.session().conversation().toChatMessages())
                .filteredOn(m -> m.role() == ChatMessage.Role.TOOL)
                .anySatisfy(m -> assertThat(m.content()).contains("子结果：核心类是 Main"));
        // 输出复用主 port：事件流里出现子代理（独立 sessionId）的事件
        assertThat(port.events).anySatisfy(e -> assertThat(e.sessionId()).isNotEqualTo(mainSession.id()));
    }
}