package com.learn.mycc.ai.model;

import java.util.List;

/**
 * 对话消息：角色、正文、（可选）关联的工具调用 ID、
 * （可选）assistant 发起的工具调用。
 * <p>作为 OpenAI 兼容协议中 messages 数组的单条元素，也是多轮工具循环中
 * 历史回填的基本单位。
 * <p>约定：{@code toolCallId} 仅在 {@code ROLE.TOOL}（工具执行结果回传）时非空；
 * {@code toolCalls} 仅由 {@code ROLE.ASSISTANT} 携带。
 */
public record ChatMessage(Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    /** 消息角色：SYSTEM 系统设定、USER 用户、ASSISTANT 助手、TOOL 工具执行结果。 */
    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    /**
     * 紧凑构造器：对 toolCalls 做防御性拷贝并保证非空，
     * 避免外部集合被修改或 null 传入导致遍历 NPE。toolCalls 可为 null。
     */
    public ChatMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 构造无工具调用的普通消息（role 为 SYSTEM/USER，toolCallId 为 null）。 */
    public static ChatMessage of(Role role, String content) {
        return new ChatMessage(role, content, null, List.of());
    }

    /** 构造工具执行结果消息（role 应为 TOOL，toolCallId 指向被调用的工具调用 id）。 */
    public static ChatMessage of(Role role, String content, String toolCallId) {
        return new ChatMessage(role, content, toolCallId, List.of());
    }

    /**
     * 携带工具调用的 assistant 消息（多轮回填协议要求原样回传 tool_calls，
     * 否则服务端会因缺少前一轮的 tool_calls 而报错）。
     */
    public static ChatMessage assistantWithTools(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, content, null, toolCalls);
    }

    /** 是否携带工具调用；供消息序列化与循环分支判断使用。 */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
