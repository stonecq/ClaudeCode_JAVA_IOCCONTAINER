package com.learn.mycc.core.permission;

/**
 * 权限判定结果（M8 权限管理的判定语言）。
 * <p>{@code ALLOW} 放行、{@code DENY} 拒绝、{@code ASK} 需人工确认。
 * 规则文件与 {@link PermissionPolicy} 的默认决策均产出该枚举。</p>
 */
public enum PermissionVerdict {
    ALLOW,
    DENY,
    ASK
}