package com.learn.mycc.core.permission;

/**
 * 权限规则存储接口：按工具名解析/记录决策。
 * <p>core 仅是纯机制层——本接口不依赖任何具体规则来源。
 * 内存态会话缓存 + 项目内持久文件由 mycc-storage 的
 * {@code JsonPermissionRuleStore} 实现，二者共同满足 M8 的「会话记忆 + 跨会话持久」。
 * 决策优先级：会话内存策略 > 项目文件策略 > 风险默认。</p>
 */
public interface PermissionRuleStore {

    /**
     * 解析某工具的既有规则决策。
     *
     * @param toolName 工具名
     * @return 命中规则的决策；无规则时返回 null
     */
    PermissionDecision resolve(String toolName);

    /**
     * 记录/覆盖某工具的决策。
     *
     * @param toolName 工具名
     * @param decision 决策，不允许为 null
     */
    void remember(String toolName, PermissionDecision decision);

    /** 清空会话内存缓存（SESSION_START 时调用），持久规则不受影响。 */
    void clearSession();
}