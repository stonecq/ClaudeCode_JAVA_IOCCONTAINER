package com.learn.mycc.hooks;

import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.annotation.ToolRisk;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.hook.HookDecision;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.permission.PermissionDecision;
import com.learn.mycc.core.permission.PermissionPolicy;
import com.learn.mycc.core.permission.PermissionRuleStore;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.core.permission.UserConfirmation.ConfirmChoice;
import com.learn.mycc.core.tool.ToolDefinition;
import com.learn.mycc.core.tool.ToolRegistry;

/**
 * 权限审批钩子：在 tool_call_before 上按风险评估工具调用并征询人工审批。
 * <p>手工构造（非 @Component）——由启动器把 CLI 专属的 {@link UserConfirmation} 注入进来，
 * 离开 IoC 扫描以避免容器无法构造该依赖。决策委托 {@link PermissionPolicy}（规则 > 风险默认）；
 * ASK 时经 confirm 征询，headless（confirm 为 null）或审批 UNAVAILABLE 一律 fail-closed 拒绝；
 * 未注册工具按 HIGH 兜底，宁可多问一层也不默认放行。SESSION_START 清空会话级规则缓存。</p>
 */
public final class PermissionHook {

    private final ToolRegistry registry;
    private final PermissionRuleStore store;
    private final PermissionPolicy policy;
    private final UserConfirmation confirm;

    /**
     * @param registry 工具注册表，用于查工具风险等级
     * @param store    规则存储，决策与持久化放行规则
     * @param policy   决策策略
     * @param confirm  人工审批交互；headless 环境传 null（fail-closed）
     */
    public PermissionHook(ToolRegistry registry, PermissionRuleStore store,
                          PermissionPolicy policy, UserConfirmation confirm) {
        this.registry = registry;
        this.store = store;
        this.policy = policy;
        this.confirm = confirm;
    }

    /**
     * 授权一次工具调用：产出放行或带原因的拒绝，供 agent 循环回填。
     *
     * @param event TOOL_CALL_BEFORE 事件，payload 为 ToolCall
     * @return 放行；或被拒时携带原因
     */
    @Hook(event = HookEventType.TOOL_CALL_BEFORE)
    public HookDecision authorize(HookEvent event) {
        if (!(event.payload() instanceof ToolCall call)) {
            return HookDecision.ALLOW;
        }
        ToolDefinition definition = find(call.name());
        ToolRisk risk = definition == null ? ToolRisk.HIGH : definition.getRisk();
        String description = definition == null ? "" : definition.getDescription();
        PermissionDecision decision = policy.decide(store, call.name(), risk);
        HookDecision hookDecision = switch (decision.verdict()) {
            case ALLOW -> HookDecision.ALLOW;
            case DENY -> HookDecision.deny(decision.reason());
            case ASK -> ask(call, description);
        };
        return hookDecision;
    }

    /**
     * 新会话开始：清空会话级规则缓存，避免恢复的旧会话继承过期内存授权。
     *
     * @param event SESSION_START 事件，payload 不使用
     * @return 恒 ALLOW（本钩子不否决此事件）
     */
    @Hook(event = HookEventType.SESSION_START)
    public HookDecision clearSession(HookEvent event) {
        store.clearSession();
        return HookDecision.ALLOW;
    }

    /** 把 ASK 的征询结果收口为 HookDecision；所有不放行的选择都落到拒绝。 */
    private HookDecision ask(ToolCall call, String description) {
        if (confirm == null) {
            return HookDecision.deny("审批环境不可用，拒绝高风险调用: " + call.name());
        }
        ConfirmChoice choice = confirm.prompt(call.name(), description, call.arguments());
        HookDecision decision = switch (choice) {
            case ALLOW_ONCE -> HookDecision.ALLOW;
            case ALLOW_ALWAYS -> {
                store.remember(call.name(), PermissionDecision.ALLOW);
                yield HookDecision.ALLOW;
            }
            case DENY, UNAVAILABLE -> HookDecision.deny("用户拒绝调用: " + call.name());
        };
        return decision;
    }

    /** 查工具定义；未注册（registry.get 抛异常）时返回 null 交由调用方按 HIGH 兜底。 */
    private ToolDefinition find(String name) {
        try {
            return registry.get(name);
        } catch (MyccException e) {
            return null;
        }
    }
}