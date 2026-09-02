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

/**
 * 离线 Mock LLM：关键字命中 / 脚本化工具调用 / 完全自定义响应脚本。
 * <p>不触网，用于测试与端到端联调：可配置关键词→回复映射，可脚本化固定工具调用，
 * 也可提供完全自定义的 {@link ChatScript} 根据完整消息历史决定响应。
 * 通过把整个响应作为单个 chunk 输出模拟流式行为。
 */
public final class MockProvider implements LlmProvider {

    /** 未命中任何关键词时的兜底回复文案。 */
    private static final String DEFAULT_REPLY = "（Mock）我收到了你的消息，但不知道如何回答。";

    /** 响应脚本：关键词模式下由 defaultScript 构造，自定义模式下为用户提供的脚本。
     * 不可为 null。 */
    private final ChatScript script;

    /** 构造"关键词→回复"模式（不脚本化工具调用）。key 为触发词，value 为回复。 */
    public MockProvider(Map<String, String> keywordResponses) {
        this(defaultScript(new HashMap<>(keywordResponses), List.of()));
    }

    /** 构造"关键词→回复 + 脚本化工具调用"模式：每次回复都固定附带给定 toolCalls。 */
    public MockProvider(Map<String, String> keywordResponses, List<ToolCall> scriptedToolCalls) {
        this(defaultScript(new HashMap<>(keywordResponses), scriptedToolCalls));
    }

    /** 快捷构造：仅固定返回脚本化工具调用（不含文本回复），用于工具循环的单测。 */
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

    /**
     * 执行一次对话：用脚本产出响应后，模拟流式分片回传。
     * <p>顺序为 思考片段 → 正文片段 → onComplete；脚本抛错时兜底转 onError，
     * 保证 StreamSink 一定以 onComplete 或 onError 结束。
     */
    @Override
    public void chat(ChatRequest request, StreamSink sink) {
        try {
            ChatResponse response = script.respond(request);
            // 思考内容与正文分别作为独立 chunk 下发，模拟真实 provider 的分段输出。
            if (response.reasoningContent() != null && !response.reasoningContent().isBlank()) {
                sink.onChunk(new StreamChunk(null, response.reasoningContent()));
            }
            if (!response.content().isEmpty()) {
                sink.onChunk(new StreamChunk(response.content()));
            }
            sink.onComplete(response);
        } catch (Exception e) {
            sink.onError(e);
        }
    }

    /** 响应脚本函数式接口：根据请求决定响应的策略，实现类无状态。 */
    @FunctionalInterface
    public interface ChatScript {
        ChatResponse respond(ChatRequest request);
    }

    /** 构造默认响应脚本：优先取关键词命中的回复，否则用兜底文案；
     * 工具调用固定回传。 */
    private static ChatScript defaultScript(Map<String, String> keywordResponses, List<ToolCall> toolCalls) {
        return request -> {
            String reply = findReply(request, keywordResponses);
            return new ChatResponse(reply, toolCalls);
        };
    }

    /**
     * 在消息历史中定位最后一条 USER 消息，并据其尝试关键词命中。
     * <p>用 reduce 保留最后一个 USER 元素；entrySet 无序，多个关键词同时命中时
     * 取第一个（顺序不保证，测试应避免冲突关键词）。
     */
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
