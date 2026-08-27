package com.learn.mycc.ai.model;

import java.util.List;

/** LLM 请求：模型、消息历史、可用工具定义、采样选项。 */
public record ChatRequest(String model, List<ChatMessage> messages, List<ToolSpec> tools, LlmOptions options) {

    public static ChatRequest of(String model, List<ChatMessage> messages) {
        return new ChatRequest(model, messages, List.of(), LlmOptions.defaults());
    }

    public ChatRequest withTools(List<ToolSpec> tools) {
        return new ChatRequest(model, messages, tools, options);
    }
}
