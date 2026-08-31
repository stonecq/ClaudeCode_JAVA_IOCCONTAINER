package com.learn.mycc.ai.model;

import java.util.List;

/** 对话消息：角色、正文、（可选）关联的工具调用 ID、（可选）assistant 发起的工具调用。 */
public record ChatMessage(Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public ChatMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatMessage of(Role role, String content) {
        return new ChatMessage(role, content, null, List.of());
    }

    public static ChatMessage of(Role role, String content, String toolCallId) {
        return new ChatMessage(role, content, toolCallId, List.of());
    }

    /** 携带工具调用的 assistant 消息（多轮回填协议要求原样回传 tool_calls）。 */
    public static ChatMessage assistantWithTools(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, content, null, toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
