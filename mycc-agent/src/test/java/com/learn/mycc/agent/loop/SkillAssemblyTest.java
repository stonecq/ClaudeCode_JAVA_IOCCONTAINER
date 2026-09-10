package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.RecordingPort;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.model.ToolSpec;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.hook.HookDefinition;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.hook.HookRegistry;
import com.learn.mycc.core.skill.SkillDefinition;
import com.learn.mycc.core.skill.SkillRegistry;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.skill.hook.SkillHook;
import com.learn.mycc.skill.tool.SkillTools;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** SkM10 技能链路端到端：SkillHook 注入技能目录、invoke_skill 工具可被 LLM 调用并取回指令。 */
class SkillAssemblyTest {

    @Test
    void sessionStartInjectsSkillCatalogAndInvokeToolIsAvailable() throws Exception {
        SkillRegistry skills = new SkillRegistry();
        skills.register(new SkillDefinition("code-review", "代码评审", "按步骤评审代码", "当用户要求代码评审时"));
        SkillHook skillHook = new SkillHook(skills);

        HookRegistry hookRegistry = new HookRegistry();
        Method method = SkillHook.class.getMethod("skillCatalogHook", HookEvent.class);
        hookRegistry.register(new HookDefinition(HookEventType.SESSION_START, skillHook, method));
        HookDispatcher dispatcher = new HookDispatcher(hookRegistry);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.postProcessAfterInitialization(new SkillTools(skills), "skillTools");

        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            captured.set(request);
            return ChatResponse.text("好的。");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 5,
                null, Session.create(), dispatcher);

        agent.run("帮我评审代码");

        assertThat(captured.get().messages().get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(captured.get().messages().get(0).content())
                .contains("可用技能")
                .contains("code-review: 代码评审");
        assertThat(captured.get().tools()).extracting(ToolSpec::name).contains("invoke_skill");
    }

    @Test
    void invokeSkillEndToEndReturnsInstructionsToLlm() {
        SkillRegistry skills = new SkillRegistry();
        skills.register(new SkillDefinition("code-review", "代码评审", "按步骤评审代码", ""));
        SkillTools skillTools = new SkillTools(skills);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.postProcessAfterInitialization(skillTools, "skillTools");

        MockProvider provider = MockProvider.scripted(request -> {
            boolean hasToolResult = request.messages().stream()
                    .anyMatch(m -> m.role() == ChatMessage.Role.TOOL);
            if (!hasToolResult) {
                return new ChatResponse("", List.of(new ToolCall("call_1", "invoke_skill", "{\"name\":\"code-review\"}")));
            }
            return ChatResponse.text("已经按技能完成评审");
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, toolRegistry, "mock", 5,
                null, Session.create(), null);

        String finalText = agent.run("帮我评审代码");

        assertThat(finalText).isEqualTo("已经按技能完成评审");
        assertThat(agent.session().conversation().toChatMessages())
                .anySatisfy(m -> {
                    assertThat(m.role()).isEqualTo(ChatMessage.Role.TOOL);
                    assertThat(m.content()).contains("按步骤评审代码");
                });
    }
}