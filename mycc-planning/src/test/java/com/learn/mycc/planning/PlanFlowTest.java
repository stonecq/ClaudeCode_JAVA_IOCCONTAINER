package com.learn.mycc.planning;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.model.ToolSpec;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** M11 规划链路端到端：LLM 建计划 → 逐步 complete_step 推进 → 全部完成，工具可经 AgentLoop 调起。 */
class PlanFlowTest {

    @TempDir
    Path tempDir;

    @Test
    void planExecutesStepByStepUntilAllDone() {
        PlanStore store = new PlanStore(new FileStorage(tempDir));
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.postProcessAfterInitialization(new PlanTools(store), "planTools");

        AtomicReference<ChatRequestHolder> captured = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            long toolCount = request.messages().stream()
                    .filter(m -> m.role() == ChatMessage.Role.TOOL)
                    .count();
            if (toolCount == 0) {
                return new ChatResponse("",
                        List.of(new ToolCall("c1", "create_plan", "{\"goal\":\"实现X\",\"steps\":\"设计\\n编码\\n测试\"}")));
            }
            if (toolCount == 1) {
                return new ChatResponse("", List.of(new ToolCall("c2", "complete_step", "{\"stepIndex\":1}")));
            }
            if (toolCount == 2) {
                return new ChatResponse("", List.of(new ToolCall("c3", "complete_step", "{\"stepIndex\":2}")));
            }
            if (toolCount == 3) {
                return new ChatResponse("", List.of(new ToolCall("c4", "complete_step", "{\"stepIndex\":3}")));
            }
            captured.compareAndSet(null, new ChatRequestHolder(request.tools()));
            return ChatResponse.text("计划完成");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 10,
                null, Session.create(), null);

        String finalText = agent.run("制定计划并逐步执行");

        assertThat(finalText).isEqualTo("计划完成");
        // 工具声明发给 LLM
        assertThat(captured.get().tools()).extracting(ToolSpec::name)
                .contains("create_plan", "complete_step");
        // 会话历史里出现最后一步的「全部完成」结果
        assertThat(agent.session().conversation().toChatMessages())
                .filteredOn(m -> m.role() == ChatMessage.Role.TOOL)
                .anySatisfy(m -> assertThat(m.content()).contains("全部步骤完成"));
        // 计划确实按会话落盘，且全部完成后被清理（不再残留旧计划）
        assertThat(store.load(agent.session().id())).isEmpty();
    }

    /** 捕获最后一次请求的工具列表。 */
    record ChatRequestHolder(List<ToolSpec> tools) {
    }
}