package com.learn.mycc.agent.session;

import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ToolCall;

import java.util.List;

/**
 * 会话内消息（含工具调用/结果），与发给 LLM 的 {@link ChatMessage} 相互转换。
 * 会话状态与 LLM API 契约分开建模：换 Provider 时只改转换层，不改会话记录。
 */
public record Message(ChatMessage.Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public Message {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message user(String content) {
        return new Message(ChatMessage.Role.USER, content, null, List.of());
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(ChatMessage.Role.ASSISTANT, content, null, toolCalls);
    }

    public static Message tool(String callId, String output) {
        return new Message(ChatMessage.Role.TOOL, output, callId, List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public ChatMessage toChatMessage() {
        return new ChatMessage(role, content, toolCallId, toolCalls);
    }
}
