package com.learn.mycc.ai.model;

import java.util.List;

/**
 * LLM 请求：模型、消息历史、可用工具定义、采样选项。
 * <p>由 {@code LlmProvider} 消费；tools 为空表示本次调用不开放工具。
 */
public record ChatRequest(String model, List<ChatMessage> messages, List<ToolSpec> tools, LlmOptions options) {

    /** 构造不含工具、采样选项取默认值的请求。 */
    public static ChatRequest of(String model, List<ChatMessage> messages) {
        return new ChatRequest(model, messages, List.of(), LlmOptions.defaults());
    }

    /** 返回携带工具定义的新请求（其余字段不变；record 不可变故返回副本）。 */
    public ChatRequest withTools(List<ToolSpec> tools) {
        return new ChatRequest(model, messages, tools, options);
    }
}
