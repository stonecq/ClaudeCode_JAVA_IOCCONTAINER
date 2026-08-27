package com.learn.mycc.ai.provider;

import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.StreamChunk;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.ai.spi.StreamSink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 离线 Mock LLM：按最后一条用户消息命中关键字返回预设回复；可配置脚本化工具调用。 */
public final class MockProvider implements LlmProvider {

    private static final String DEFAULT_REPLY = "（Mock）我收到了你的消息，但不知道如何回答。";

    private final Map<String, String> keywordResponses;
    private final List<ToolCall> scriptedToolCalls;

    public MockProvider(Map<String, String> keywordResponses) {
        this(keywordResponses, List.of());
    }

    public MockProvider(Map<String, String> keywordResponses, List<ToolCall> scriptedToolCalls) {
        this.keywordResponses = new HashMap<>(keywordResponses);
        this.scriptedToolCalls = List.copyOf(scriptedToolCalls);
    }

    public static MockProvider withToolCalls(List<ToolCall> toolCalls) {
        return new MockProvider(Map.of(), toolCalls);
    }

    @Override
    public void chat(ChatRequest request, StreamSink sink) {
        String reply = findReply(request);
        ChatResponse response = new ChatResponse(reply, scriptedToolCalls);
        sink.onChunk(new StreamChunk(reply));
        sink.onComplete(response);
    }

    private String findReply(ChatRequest request) {
        String lastUserContent = request.messages().stream()
                .filter(message -> message.role() == ChatMessage.Role.USER)
                .reduce((first, second) -> second)
                .map(ChatMessage::content)
                .orElse("");
        return keywordResponses.entrySet().stream()
                .filter(entry -> lastUserContent.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(DEFAULT_REPLY);
    }
}
