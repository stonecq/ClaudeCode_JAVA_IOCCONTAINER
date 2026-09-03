package com.learn.mycc.core.permission;

/**
 * 人工审批交互端口（core 纯接口）。
 * <p>当 {@link PermissionPolicy} 产出 ASK 时，由具体 UI 实现征求用户选择——
 * mycc-cli 的 {@code CliPermissionPrompt} 做终端行交互，未来 Web 端可挂接第三方
 * 审批（M13）。headless 环境（实现不可用/读取失败）返回 {@link ConfirmChoice#UNAVAILABLE}，
 * 由调用方按 fail-closed（拒绝）降级。</p>
 */
public interface UserConfirmation {

    /**
     * 征求用户对一次工具调用的审批选择。
     *
     * @param toolName    工具名
     * @param description 工具描述
     * @param args        调用参数（JSON 字符串，仅用于知情展示）
     * @return 用户选择；不可用（无控制台/读取失败）返回 {@link ConfirmChoice#UNAVAILABLE}
     */
    ConfirmChoice prompt(String toolName, String description, String args);

    /** 用户可能的审批选择（M8 交互为 {@code [y/N/a]}）。 */
    enum ConfirmChoice {
        /** 仅放行本次调用，不持久化。 */
        ALLOW_ONCE,
        /** 拒绝本次调用，不持久化。 */
        DENY,
        /** 放行并持久化此工具的放行规则（会话 + 项目文件）。 */
        ALLOW_ALWAYS,
        /** 审批不可用；调用方应降级为拒绝。 */
        UNAVAILABLE
    }
}