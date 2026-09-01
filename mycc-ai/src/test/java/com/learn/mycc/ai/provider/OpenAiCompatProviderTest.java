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
    private volatile String receivedBody;

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
    void streamsReasoningContentAndKeepsItInResponse() {
        String sse = """
                data: {"choices":[{"delta":{"reasoning_content":"让我想想 "},"finish_reason":null}]}

                data: {"choices":[{"delta":{"reasoning_content":"先分析一下"},"finish_reason":null}]}

                data: {"choices":[{"delta":{"content":"最终答案"},"finish_reason":null}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        route(sse, 200);
        RecordingStreamSink sink = new RecordingStreamSink();

        provider().chat(request(), sink);

        assertThat(sink.error).isNull();
        assertThat(sink.response.reasoningContent()).isEqualTo("让我想想 先分析一下");
        assertThat(sink.response.content()).isEqualTo("最终答案");
        assertThat(sink.chunks).extracting(chunk -> chunk.reasoningContent())
                .containsExactly("让我想想 ", "先分析一下", null);
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

    @Test
    void echoesAssistantToolCallsInRequestBody() {
        route("data: [DONE]\n\n", 200);
        RecordingStreamSink sink = new RecordingStreamSink();
        ChatRequest req = ChatRequest.of("gpt-test", List.of(
                ChatMessage.assistantWithTools("", List.of(new ToolCall("call_1", "read_file", "{\"path\":\"a.txt\"}")))));

        provider().chat(req, sink);

        assertThat(sink.error).isNull();
        assertThat(receivedBody).contains("\"tool_calls\"");
        assertThat(receivedBody).contains("\"call_1\"");
        assertThat(receivedBody).contains("\"read_file\"");
    }

    private void route(String sseBody, int status) {
        server.createContext("/v1/chat/completions", exchange -> {
            receivedAuth = exchange.getRequestHeaders().getFirst("Authorization");
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
