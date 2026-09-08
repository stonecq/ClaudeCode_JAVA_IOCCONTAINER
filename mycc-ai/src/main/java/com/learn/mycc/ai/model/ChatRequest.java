package com.learn.mycc.ai.model;

import java.util.List;

/**
 * LLM 请求：模型、消息历史、可用工具定义、采样选项、会话标识。
 * <p>由 {@code LlmProvider} 消费；tools 为空表示本次调用不开放工具。
 * conversationId 为对话线程的中性标识（如 agent 会话 id），属通用事实——具体厂商是否/如何
 * 把它编码成传输头（如某网关的 x-opencode-session），由 provider 自行决定，核心不感知。
 * 可为 null，表示调用方不提供会话上下文。</p>
 */
public record ChatRequest(String model, List<ChatMessage> messages, List<ToolSpec> tools, LlmOptions options, String conversationId) {

    /** 构造不含工具、采样选项取默认值、无会话标识的请求。 */
    public static ChatRequest of(String model, List<ChatMessage> messages) {
        return new ChatRequest(model, messages, List.of(), LlmOptions.defaults(), null);
    }

    /** 返回携带工具定义的新请求（record 不可变故返回副本）。 */
    public ChatRequest withTools(List<ToolSpec> tools) {
        return new ChatRequest(model, messages, tools, options, conversationId);
    }

    /** 返回携带对话标识的新请求（通用事实；是否编码进传输头由 provider 决定）。 */
    public ChatRequest withConversationId(String conversationId) {
        return new ChatRequest(model, messages, tools, options, conversationId);
    }
}