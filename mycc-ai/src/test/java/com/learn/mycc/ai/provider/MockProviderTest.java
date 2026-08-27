package com.learn.mycc.ai.provider;

import com.learn.mycc.ai.RecordingStreamSink;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockProviderTest {

    private ChatRequest request(String userContent) {
        return ChatRequest.of("mock", List.of(ChatMessage.of(ChatMessage.Role.USER, userContent)));
    }

    @Test
    void streamsChunkThenCompletes() {
        MockProvider provider = new MockProvider(Map.of());
        RecordingStreamSink sink = new RecordingStreamSink();

        provider.chat(request("随便说点什么"), sink);

        assertThat(sink.error).isNull();
        assertThat(sink.chunks).hasSize(1);
        assertThat(sink.response).isNotNull();
        assertThat(sink.response.content()).isEqualTo(sink.chunks.get(0).content());
    }

    @Test
    void returnsReplyWhenUserMessageHitsKeyword() {
        MockProvider provider = new MockProvider(Map.of("你好", "你好！我是 Mock。", "再见", "拜拜。"));
        RecordingStreamSink sink = new RecordingStreamSink();

        provider.chat(request("你好呀"), sink);

        assertThat(sink.response.content()).isEqualTo("你好！我是 Mock。");
    }

    @Test
    void returnsDefaultReplyWhenNoKeywordMatches() {
        MockProvider provider = new MockProvider(Map.of("你好", "你好！"));
        RecordingStreamSink sink = new RecordingStreamSink();

        provider.chat(request("今天天气如何"), sink);

        assertThat(sink.response.content()).contains("Mock");
        assertThat(sink.response.hasToolCalls()).isFalse();
    }

    @Test
    void returnsScriptedToolCalls() {
        MockProvider provider = MockProvider.withToolCalls(List.of(
                new ToolCall("call_1", "read_file", "{\"path\":\"a.txt\"}")));
        RecordingStreamSink sink = new RecordingStreamSink();

        provider.chat(request("读取文件 a.txt"), sink);

        assertThat(sink.response.hasToolCalls()).isTrue();
        assertThat(sink.response.toolCalls()).hasSize(1);
        assertThat(sink.response.toolCalls().get(0).name()).isEqualTo("read_file");
        assertThat(sink.response.toolCalls().get(0).arguments()).contains("\"a.txt\"");
    }
}
