package com.learn.mycc.hooks;

import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.annotation.ToolRisk;
import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import com.learn.mycc.core.hook.HookDecision;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.permission.PermissionDecision;
import com.learn.mycc.core.permission.PermissionPolicy;
import com.learn.mycc.core.permission.PermissionRuleStore;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.core.permission.UserConfirmation.ConfirmChoice;
import com.learn.mycc.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionHookTest {

    private ToolRegistry registry;
    private FakeStore store;
    private PermissionPolicy policy;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        BeanFactory factory = new BeanFactory();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(FixtureTools.class));
        factory.getBean(FixtureTools.class);
        store = new FakeStore();
        policy = new PermissionPolicy();
    }

    private HookDecision authorize(UserConfirmation confirm, String tool, String args) {
        return new PermissionHook(registry, store, policy, confirm)
                .authorize(new HookEvent(HookEventType.TOOL_CALL_BEFORE, "s1",
                        new ToolCall("c1", tool, args)));
    }

    private static HookEvent sessionStart() {
        return new HookEvent(HookEventType.SESSION_START, "s1", null);
    }

    @Test
    void lowRiskToolAllowedWithoutPrompt() {
        // confirm 为 null：若真的走了审批必然 NPE，放行即证明未触发征询
        assertThat(authorize(null, "read_file", "{\"path\":\"a.txt\"}").allowed()).isTrue();
    }

    @Test
    void highRiskAskAllowOnceAllows() {
        FakeConfirm confirm = new FakeConfirm(ConfirmChoice.ALLOW_ONCE);
        assertThat(authorize(confirm, "bash", "echo hi").allowed()).isTrue();
        assertThat(confirm.calls).isEqualTo(1);
        assertThat(store.rememberCount).isZero();
    }

    @Test
    void highRiskAskDenyDenies() {
        FakeConfirm confirm = new FakeConfirm(ConfirmChoice.DENY);
        HookDecision decision = authorize(confirm, "bash", "echo hi");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("拒绝");
    }

    @Test
    void highRiskUnavailableChoiceDenied() {
        // UNAVAILABLE 与拒绝同等对待，fail-closed 不放行
        FakeConfirm confirm = new FakeConfirm(ConfirmChoice.UNAVAILABLE);
        assertThat(authorize(confirm, "bash", "echo hi").allowed()).isFalse();
    }

    @Test
    void highRiskAllowAlwaysPersistsRule() {
        FakeConfirm confirm = new FakeConfirm(ConfirmChoice.ALLOW_ALWAYS);
        assertThat(authorize(confirm, "bash", "echo hi").allowed()).isTrue();
        assertThat(store.resolve("bash").allowed()).isTrue();
        assertThat(store.rememberCount).isEqualTo(1);
    }

    @Test
    void allowRuleSkipsAskForHighRisk() {
        store.remember("bash", PermissionDecision.ALLOW);
        FakeConfirm confirm = new FakeConfirm(ConfirmChoice.DENY);
        assertThat(authorize(confirm, "bash", "echo hi").allowed()).isTrue();
        assertThat(confirm.calls).isZero();
    }

    @Test
    void denyRuleOverridesLowRisk() {
        store.remember("read_file", PermissionDecision.deny("目录只读"));
        HookDecision decision = authorize(null, "read_file", "{}");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("目录只读");
    }

    @Test
    void headlessHighRiskDenied() {
        // headless（confirm 为 null）时高风险调用 fail-closed 拒绝
        HookDecision decision = authorize(null, "bash", "echo hi");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("审批");
    }

    @Test
    void unknownToolAskedThenHeadlessDenied() {
        // 未注册工具按 HIGH 兜底：先过审批再放行，而非默认放行
        FakeConfirm confirm = new FakeConfirm(ConfirmChoice.ALLOW_ONCE);
        assertThat(authorize(confirm, "deploy", "{}").allowed()).isTrue();
        assertThat(confirm.calls).isEqualTo(1);
        // headless 下同款未知工具 fail-closed 拒绝
        assertThat(authorize(null, "deploy", "{}").allowed()).isFalse();
    }

    @Test
    void nonToolCallPayloadAllowed() {
        PermissionHook hook = new PermissionHook(registry, store, policy, null);
        assertThat(hook.authorize(new HookEvent(HookEventType.TOOL_CALL_BEFORE, "s1", "text")).allowed()).isTrue();
    }

    @Test
    void sessionStartClearsSession() {
        store.remember("bash", PermissionDecision.ALLOW);
        new PermissionHook(registry, store, policy, null).clearSession(sessionStart());
        assertThat(store.resolve("bash")).isNull();
        assertThat(store.clearCount).isEqualTo(1);
    }

    /** 测试用工具：bash 高险、read_file 低险，供注册表按风险分流。 */
    @Component
    static class FixtureTools {

        @Tool(name = "bash", description = "执行 shell 命令", risk = ToolRisk.HIGH)
        public String bash(@ToolParam(description = "命令") String command) {
            return "executed: " + command;
        }

        @Tool(name = "read_file", description = "读取文件内容", risk = ToolRisk.LOW)
        public String read(@ToolParam(description = "路径") String path) {
            return "content";
        }
    }

    /** 内存态规则存储：记住即写入会话与项目两份，clearSession 只清会话。 */
    static class FakeStore implements PermissionRuleStore {

        private final Map<String, PermissionDecision> session = new HashMap<>();
        int rememberCount;
        int clearCount;

        @Override
        public PermissionDecision resolve(String toolName) {
            return session.containsKey(toolName) ? session.get(toolName)
                    : null;
        }

        @Override
        public void remember(String toolName, PermissionDecision decision) {
            session.put(toolName, decision);
            rememberCount++;
        }

        @Override
        public void clearSession() {
            session.clear();
            clearCount++;
        }
    }

    /** 固定返回预设选择的假审批交互。 */
    static class FakeConfirm implements UserConfirmation {

        private final ConfirmChoice choice;
        int calls;

        FakeConfirm(ConfirmChoice choice) {
            this.choice = choice;
        }

        @Override
        public ConfirmChoice prompt(String toolName, String description, String args) {
            calls++;
            return choice;
        }
    }
}