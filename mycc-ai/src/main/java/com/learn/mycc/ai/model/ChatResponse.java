package com.learn.mycc.ai.model;

import java.util.List;

/**
 * LLM 响应：正文文本与/或一系列工具调用。
 * <p>分两种情况使用：流式接口在 {@code StreamSink.onComplete} 时下发完整结果；
 * 亦作为 Mock 等非流式 provider 的产出。reasoningContent 为思考过程
 * （DeepSeek 特有），可为 null。
 */
public record ChatResponse(String content, String reasoningContent, List<ToolCall> toolCalls) {

    /** 构造无思考内容的响应（reasoningContent 置 null）。 */
    public ChatResponse(String content, List<ToolCall> toolCalls) {
        this(content, null, toolCalls);
    }

    /** 构造纯文本响应（无工具调用、含思考内容）。 */
    public static ChatResponse text(String content, String reasoningContent) {
        return new ChatResponse(content, reasoningContent,List.of());
    }

    /** 构造纯文本响应（无思考内容、无工具调用）。 */
    public static ChatResponse text(String content) {
        return new ChatResponse(content, null,List.of());
    }

    /** 是否携带工具调用；供 agent 循环判断是否需要继续执行工具分支。 */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

}
