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

/** 离线 Mock LLM：关键字命中 / 脚本化工具调用 / 完全自定义响应脚本。 */
public final class MockProvider implements LlmProvider {

    private static final String DEFAULT_REPLY = "（Mock）我收到了你的消息，但不知道如何回答。";

    private final ChatScript script;

    public MockProvider(Map<String, String> keywordResponses) {
        this(defaultScript(new HashMap<>(keywordResponses), List.of()));
    }

    public MockProvider(Map<String, String> keywordResponses, List<ToolCall> scriptedToolCalls) {
        this(defaultScript(new HashMap<>(keywordResponses), scriptedToolCalls));
    }

    public static MockProvider withToolCalls(List<ToolCall> toolCalls) {
        return new MockProvider(Map.of(), toolCalls);
    }

    /** 完全自定义：根据请求（含完整消息历史）决定响应，用于多轮工具循环端到端。 */
    public static MockProvider scripted(ChatScript script) {
        return new MockProvider(script);
    }

    private MockProvider(ChatScript script) {
        this.script = script;
    }

    @Override
    public void chat(ChatRequest request, StreamSink sink) {
        try {
            ChatResponse response = script.respond(request);
            if (!response.content().isEmpty()) {
                sink.onChunk(new StreamChunk(response.content()));
            }
            sink.onComplete(response);
        } catch (Exception e) {
            sink.onError(e);
        }
    }

    @FunctionalInterface
    public interface ChatScript {
        ChatResponse respond(ChatRequest request);
    }

    private static ChatScript defaultScript(Map<String, String> keywordResponses, List<ToolCall> toolCalls) {
        return request -> {
            String reply = findReply(request, keywordResponses);
            return new ChatResponse(reply, toolCalls);
        };
    }

    private static String findReply(ChatRequest request, Map<String, String> keywordResponses) {
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
