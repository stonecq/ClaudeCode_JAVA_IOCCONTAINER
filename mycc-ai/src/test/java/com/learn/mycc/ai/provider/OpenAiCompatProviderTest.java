package com.learn.mycc.ai.provider;

import com.learn.mycc.ai.RecordingStreamSink;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ModelConfig;
import com.learn.mycc.ai.model.ToolCall;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatProviderTest {

    private HttpServer server;
    private volatile String receivedAuth;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void streamsTextFromSse() {
        String sse = """
                data: {"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":" World"},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        route(sse, 200);
        RecordingStreamSink sink = new RecordingStreamSink();

        provider().chat(request(), sink);

        assertThat(sink.error).isNull();
        assertThat(sink.response.content()).isEqualTo("Hello World");
        assertThat(sink.chunks).extracting(chunk -> chunk.content()).containsExactly("Hello", " World");
    }

    @Test
    void parsesToolCalls() {
        String sse = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"read_file","arguments":""}}]},"finish_reason":null}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"path\\":\\"a.txt\\"}"}}]},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                data: [DONE]

                """;
        route(sse, 200);
        RecordingStreamSink sink = new RecordingStreamSink();

        provider().chat(request(), sink);

        assertThat(sink.response.hasToolCalls()).isTrue();
        assertThat(sink.response.toolCalls()).hasSize(1);
        ToolCall call = sink.response.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call_1");
        assertThat(call.name()).isEqualTo("read_file");
        assertThat(call.arguments()).isEqualTo("{\"path\":\"a.txt\"}");
    }

    @Test
    void sendsAuthorizationHeader() {
        route("data: [DONE]\n\n", 200);
        RecordingStreamSink sink = new RecordingStreamSink();

        provider().chat(request(), sink);

        assertThat(receivedAuth).isEqualTo("Bearer test-key");
    }

    @Test
    void surfacesHttpErrorToSink() {
        route("", 401);
        RecordingStreamSink sink = new RecordingStreamSink();

        provider().chat(request(), sink);

        assertThat(sink.response).isNull();
        assertThat(sink.error).isNotNull();
        assertThat(sink.error.getMessage()).contains("401");
    }

    private void route(String sseBody, int status) {
        server.createContext("/v1/chat/completions", exchange -> {
            receivedAuth = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = sseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private OpenAiCompatProvider provider() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
        return new OpenAiCompatProvider(new ModelConfig("test-key", baseUrl));
    }

    private ChatRequest request() {
        return ChatRequest.of("gpt-test", List.of(ChatMessage.of(ChatMessage.Role.USER, "hi")));
    }
}
