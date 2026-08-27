package com.learn.mycc.ai.model;

import java.util.List;

/** LLM 响应：正文文本与/或一系列工具调用。 */
public record ChatResponse(String content, List<ToolCall> toolCalls) {

    public static ChatResponse text(String content) {
        return new ChatResponse(content, List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
