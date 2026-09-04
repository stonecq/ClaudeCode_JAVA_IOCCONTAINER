package com.learn.mycc.core.permission;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.ToolRisk;

/**
 * 权限决策策略（纯函数）：给定规则存储、工具名与风险等级产出决策。
 * <p>决策优先级：规则命中（会话 > 项目）优先于风险默认；无规则时
 * HIGH 风险询问、LOW 风险放行。core 保持纯机制层——本类不知道
 * ToolCall 等具体类型，按工具名字符串决策。</p>
 */
@Component
public final class PermissionPolicy {

    /**
     * 对一次工具调用做出权限决策。
     *
     * @param store 规则存储（可为空实现）
     * @param toolName 工具名
     * @param risk 工具风险等级
     * @return 决策结果
     */
    public PermissionDecision decide(PermissionRuleStore store, String toolName, ToolRisk risk) {
        PermissionDecision rule = store.resolve(toolName);
        if (rule != null) {
            return rule;
        }
        return risk == ToolRisk.HIGH ? PermissionDecision.ASK : PermissionDecision.ALLOW;
    }
}