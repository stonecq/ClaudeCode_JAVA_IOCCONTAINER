package com.learn.mycc.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ModelConfig;
import com.learn.mycc.ai.model.StreamChunk;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.model.ToolSpec;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.ai.spi.StreamSink;
import com.learn.mycc.core.exception.MyccException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** 兼容 OpenAI /chat/completions 流式接口的 Provider（SSE 解析 + tool_calls 累积）。 */
public final class OpenAiCompatProvider implements LlmProvider {

    private static final String DONE_MARKER = "[DONE]";

    private final ModelConfig config;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatProvider(ModelConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    OpenAiCompatProvider(ModelConfig config, HttpClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public void chat(ChatRequest request, StreamSink sink) {
        try {
            HttpRequest httpRequest = buildRequest(request);
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new MyccException("LLM 接口返回 " + response.statusCode() + ": " + errorBody);
            }
            parseStream(response.body(), sink);
        } catch (MyccException e) {
            sink.onError(e);
        } catch (Exception e) {
            sink.onError(new MyccException("调用 LLM 接口失败", e));
        }
    }

    private HttpRequest buildRequest(ChatRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.model());
        body.put("stream", true);
        if (request.options().temperature() != null) {
            body.put("temperature", request.options().temperature());
        }
        if (request.options().maxTokens() != null) {
            body.put("max_tokens", request.options().maxTokens());
        }
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode node = messages.addObject();
            node.put("role", message.role().name().toLowerCase());
            node.put("content", message.content() == null ? "" : message.content());
            if (message.toolCallId() != null) {
                node.put("tool_call_id", message.toolCallId());
            }
            if (message.hasToolCalls()) {
                ArrayNode toolCalls = node.putArray("tool_calls");
                for (ToolCall call : message.toolCalls()) {
                    ObjectNode toolCall = toolCalls.addObject();
                    toolCall.put("id", call.id());
                    toolCall.put("type", "function");
                    ObjectNode function = toolCall.putObject("function");
                    function.put("name", call.name());
                    function.put("arguments", call.arguments());
                }
            }
        }
        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec spec : request.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", spec.name());
                function.put("description", spec.description());
                function.set("parameters", mapper.valueToTree(spec.parameters()));
            }
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private void parseStream(InputStream input, StreamSink sink) {
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCallBuilder> toolCallBuilders = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.equals(DONE_MARKER)) {
                    break;
                }
                JsonNode event = mapper.readTree(data);
                JsonNode choices = event.path("choices");
                if (choices.isEmpty()) {
                    continue;
                }
                JsonNode delta = choices.get(0).path("delta");
                String deltaContent = delta.path("content").asText(null);
                if (deltaContent != null) {
                    content.append(deltaContent);
                    sink.onChunk(new StreamChunk(deltaContent));
                }
                for (JsonNode toolCall : delta.path("tool_calls")) {
                    int index = toolCall.path("index").asInt();
                    ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(index, i -> new ToolCallBuilder());
                    if (toolCall.has("id")) {
                        builder.id = toolCall.path("id").asText();
                    }
                    JsonNode function = toolCall.path("function");
                    if (function.has("name")) {
                        builder.name = function.path("name").asText();
                    }
                    builder.arguments.append(function.path("arguments").asText(""));
                }
            }
        } catch (IOException e) {
            throw new MyccException("解析 LLM 流式响应失败", e);
        }
        java.util.List<ToolCall> toolCalls = toolCallBuilders.values().stream()
                .map(builder -> new ToolCall(builder.id, builder.name, builder.arguments.toString()))
                .toList();
        sink.onComplete(new ChatResponse(content.toString(), toolCalls));
    }

    private static final class ToolCallBuilder {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }
}
