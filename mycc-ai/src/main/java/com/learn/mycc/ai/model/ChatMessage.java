package com.learn.mycc.ai.model;

/** 对话消息：角色、正文、（可选）关联的工具调用 ID。 */
public record ChatMessage(Role role, String content, String toolCallId) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public static ChatMessage of(Role role, String content) {
        return new ChatMessage(role, content, null);
    }
}
