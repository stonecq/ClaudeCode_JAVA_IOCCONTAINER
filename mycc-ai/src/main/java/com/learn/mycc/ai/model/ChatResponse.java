package com.learn.mycc.ai.model;

import java.util.List;

/** LLM 响应：正文文本与/或一系列工具调用。 */
public record ChatResponse(String content, String reasoningContent, List<ToolCall> toolCalls) {

    public ChatResponse(String content, List<ToolCall> toolCalls) {
        this(content, null, toolCalls);
    }

    public static ChatResponse text(String content, String reasoningContent) {
        return new ChatResponse(content, reasoningContent,List.of());
    }

    public static ChatResponse text(String content) {
        return new ChatResponse(content, null,List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

}
