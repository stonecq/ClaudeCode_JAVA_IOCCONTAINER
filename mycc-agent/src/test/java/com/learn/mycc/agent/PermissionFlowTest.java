package com.learn.mycc.agent;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.core.hook.HookRegistry;
import com.learn.mycc.core.permission.PermissionPolicy;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.core.permission.UserConfirmation.ConfirmChoice;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.hooks.PermissionHook;
import com.learn.mycc.storage.permission.JsonPermissionRuleStore;
import com.learn.mycc.tools.BashTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M8 验收：真实 PermissionHook + JsonPermissionRuleStore + BashTool 的权限审批端到端。
 * <p>MockProvider 首轮返回高风险 bash 调用，AgentLoop 派发 tool_call_before 给审批钩子；
 * 依 confirm 假实现的所选：放行则执行（回填 TOOL 结果），拒绝则工具不执行、以「被钩子拦截」
 * 回填。规则持久化落在 {@code <workspace>/.mycc/permissions.json}（@TempDir 工作区）。</p>
 */
class PermissionFlowTest {

    @TempDir
    Path workspace;

    private ToolRegistry registry;
    private JsonPermissionRuleStore store;
    private PermissionPolicy policy;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new BashTool(), "bashTool");
        store = new JsonPermissionRuleStore(new ApplicationConfig(workspace));
        policy = new PermissionPolicy();
    }

    /** 用真实 PermissionHook 装配 agent：confirm 可为 null（headless，fail-closed）。 */
    private AgentLoop agent(UserConfirmation confirm) {
        HookRegistry hookRegistry = new HookRegistry();
        hookRegistry.postProcessAfterInitialization(
                new PermissionHook(registry, store, policy, confirm), "permissionHook");
        RecordingPort port = new RecordingPort();
        return AgentLoop.withToolRegistry(port, provider(), registry, "mock", 10,
                null, Session.create(), new HookDispatcher(hookRegistry));
    }

    /** 首轮抛一个 bash 高险调用；TOOL 结果返回后即给最终回复。 */
    private static MockProvider provider() {
        return MockProvider.scripted(request -> {
            boolean hasToolResult = request.messages().stream()
                    .anyMatch(m -> m.role() == ChatMessage.Role.TOOL);
            if (!hasToolResult) {
                return new ChatResponse("", List.of(new ToolCall("call_1", "bash",
                        "{\"command\":\"echo hi\"}")));
            }
            return ChatResponse.text("done");
        });
    }

    @Test
    void allowOnceExecutesCommandWithoutPersisting() throws IOException {
        AgentLoop agent = agent(new FixedConfirm(ConfirmChoice.ALLOW_ONCE));

        String finalText = agent.run("echo hi");

        assertThat(finalText).isEqualTo("done");
        // 工具已执行：TOOL 消息回填 BashResult，断言 exitCode=0 屏蔽平台差异
        assertThat(agent.session().conversation().toChatMessages())
                .anySatisfy(m -> {
                    assertThat(m.role()).isEqualTo(ChatMessage.Role.TOOL);
                    assertThat(m.content()).contains("exitCode=0");
                });
        // 允许本次不持久化：无会话规则，也无落盘文件
        assertThat(store.resolve("bash")).isNull();
        assertThat(Files.exists(workspace.resolve(".mycc").resolve("permissions.json"))).isFalse();
    }

    @Test
    void denyBlocksExecutionAndFeedsBack() {
        AgentLoop agent = agent(new FixedConfirm(ConfirmChoice.DENY));

        String finalText = agent.run("echo hi");

        assertThat(finalText).isEqualTo("done");
        // 拒绝后工具未执行：回填「被钩子拦截: 用户拒绝调用: bash」
        assertThat(agent.session().conversation().toChatMessages())
                .anySatisfy(m -> {
                    assertThat(m.role()).isEqualTo(ChatMessage.Role.TOOL);
                    assertThat(m.content()).isEqualTo("被钩子拦截: 用户拒绝调用: bash");
                });
        assertThat(store.resolve("bash")).isNull();
    }

    @Test
    void allowAlwaysExecutesAndPersistsRule() throws IOException {
        AgentLoop agent = agent(new FixedConfirm(ConfirmChoice.ALLOW_ALWAYS));

        String finalText = agent.run("echo hi");

        assertThat(finalText).isEqualTo("done");
        // 始终允许：会话内存 + 项目 JSON 双落盘
        assertThat(store.resolve("bash").allowed()).isTrue();
        Path file = workspace.resolve(".mycc").resolve("permissions.json");
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readString(file)).contains("\"bash\" : \"ALLOW\"");
    }

    @Test
    void headlessDeniesHighRiskCall() {
        AgentLoop agent = agent(null);

        String finalText = agent.run("echo hi");

        assertThat(finalText).isEqualTo("done");
        // headless（confirm 为 null）高风险调用 fail-closed：工具未执行
        assertThat(agent.session().conversation().toChatMessages())
                .anySatisfy(m -> {
                    assertThat(m.role()).isEqualTo(ChatMessage.Role.TOOL);
                    assertThat(m.content()).isEqualTo("被钩子拦截: 审批环境不可用，拒绝高风险调用: bash");
                });
    }

    /** 固定返回预设选择的假审批交互。 */
    private static final class FixedConfirm implements UserConfirmation {

        private final ConfirmChoice choice;

        FixedConfirm(ConfirmChoice choice) {
            this.choice = choice;
        }

        @Override
        public ConfirmChoice prompt(String toolName, String description, String args) {
            return choice;
        }
    }
}