package com.learn.mycc.ai.provider;

import com.learn.mycc.ai.RecordingStreamSink;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ModelConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** opencode 网关子类：只验证「会话标识 → x-opencode-session 头」的厂商约定，其余走基类。 */
class OpenCodeGatewayProviderTest {

    private HttpServer server;
    private volatile String receivedSession;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsSessionHeaderForConversationId() {
        route();
        RecordingStreamSink sink = new RecordingStreamSink();

        provider().chat(request().withConversationId("conv-1"), sink);

        assertThat(receivedSession).isEqualTo("conv-1");
    }

    @Test
    void omitsSessionHeaderWithoutConversationId() {
        route();
        RecordingStreamSink sink = new RecordingStreamSink();

        provider().chat(request(), sink);

        assertThat(receivedSession).isNull();
    }

    private void route() {
        String sse = "data: [DONE]\n\n";
        server.createContext("/v1/chat/completions", exchange -> {
            receivedSession = exchange.getRequestHeaders().getFirst("x-opencode-session");
            byte[] body = sse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private OpenCodeGatewayProvider provider() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
        return new OpenCodeGatewayProvider(new ModelConfig("test-key", baseUrl));
    }

    private ChatRequest request() {
        return ChatRequest.of("gpt-test", List.of(ChatMessage.of(ChatMessage.Role.USER, "hi")));
    }
}