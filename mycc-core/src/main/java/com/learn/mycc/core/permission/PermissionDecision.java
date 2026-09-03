package com.learn.mycc.core.permission;

/**
 * 权限决策值对象：判定结果 + 拒绝原因。
 * <p>{@code ALLOW}/{@code ASK} 常量用于最常见的放行与询问；
 * 拒绝用静态工厂 {@link #deny(String)} 携带原因，供 agent 循环回填给 LLM。
 * 不可变 record。</p>
 */
public record PermissionDecision(PermissionVerdict verdict, String reason) {

    /** 放行决策：无原因。 */
    public static final PermissionDecision ALLOW = new PermissionDecision(PermissionVerdict.ALLOW, null);

    /** 询问决策：需要人工确认，无预设原因。 */
    public static final PermissionDecision ASK = new PermissionDecision(PermissionVerdict.ASK, null);

    /** @return 拒绝决策，携带原因 */
    public static PermissionDecision deny(String reason) {
        return new PermissionDecision(PermissionVerdict.DENY, reason);
    }

    /** @return 是否放行（仅 ALLOW） */
    public boolean allowed() {
        return verdict == PermissionVerdict.ALLOW;
    }
}