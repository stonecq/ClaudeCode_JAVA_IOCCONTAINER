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
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
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

/**
 * 兼容 OpenAI /chat/completions 流式接口的 Provider（SSE 解析 + tool_calls 累积）。
 * <p>通过真实的 HTTP(S) 调用 DeepSeek 等 OpenAI 兼容服务，处理流式 SSE、增量 delta 累加、
 * 思考内容（reasoning_content）、工具调用分段拼装及各类网络错误。保持**纯 OpenAI 兼容**——
 * 厂商专属请求头不在此硬编码，经 {@link #applyVendorHeaders} 钩子由子类注入
 * （如 opencode 网关的 {@code x-opencode-session}）。
 * 与 {@link MockProvider} 行为对齐：均通过 {@link StreamSink} 回调。</p>
 */
public class OpenAiCompatProvider implements LlmProvider {

    /** SSE 流结束标记：遇到该行表示服务端已完成全部输出。 */
    private static final String DONE_MARKER = "[DONE]";

    /** 连接配置（API Key + 基址），不应为 null。 */
    private final ModelConfig config;
    /** HTTP 客户端；可注入以便测试替换为 MockWebServer。 */
    private final HttpClient client;
    /** Jackson 映射器，用于请求体构造与 SSE 数据反序列化。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /** 使用默认 HttpClient 构造，便于生产直接使用。 */
    public OpenAiCompatProvider(ModelConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    /** 包私有构造：注入自定义 HttpClient，供测试替换网络层。 */
    OpenAiCompatProvider(ModelConfig config, HttpClient client) {
        this.config = config;
        this.client = client;
    }

    /**
     * 发起一次流式对话。
     * <p>先构建并发送 HTTP 请求，非 200 时读取错误体并转为异常；200 时解析 SSE 流。
     * 所有失败（网络、HTTP 错误、解析错误）统一转为 onError，保证回调以完成/错误收尾。
     */
    @Override
    public void chat(ChatRequest request, StreamSink sink) {
        try {
            HttpRequest httpRequest = buildRequest(request);
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                // 非 200：读取完整错误体以便提供可诊断信息后抛异常。
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new MyccException("LLM 接口返回 " + response.statusCode() + ": " + errorBody);
            }
            parseStream(response.body(), sink);
        } catch (MyccException e) {
            // 已包装的业务异常直接透传，避免二次包装混淆根因。
            sink.onError(e);
        } catch (Exception e) {
            // 网络超时/连接中断等底层异常在此统一包装为 MyccException。
            sink.onError(new MyccException("调用 LLM 接口失败", e));
        }
    }

    /**
     * 将 ChatRequest 序列化为 OpenAI 兼容格式的请求体并构建 HttpRequest。
     * <p>要点：toolCallId 映射为 tool_call_id；assistant 消息中的 tool_calls 原样回传
     * （多轮协议要求）；工具定义以 JSON Schema 写入 tools；options 仅在非 null 时写入，
     * 缺失字段由服务端取默认值。
     */
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
            // OpenAI 协议角色为小写字符串，这里由枚举名转小写。
            node.put("role", message.role().name().toLowerCase());
            // content 为 null 时补空串，避免部分服务端拒绝缺字段的消息。
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
                // 将 Map 形式的 JSON Schema 转为树节点后嵌入，避免重复字符串化。
                function.set("parameters", mapper.valueToTree(spec.parameters()));
            }
        }
        HttpRequest.Builder post = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json");
        // 厂商专属头走子类钩子（默认无）：本类保持纯 OpenAI 兼容，不内建任何厂商约定
        applyVendorHeaders(post, request);
        return post.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
    }

    /**
     * 厂商专属请求头扩展点（默认 no-op）。子类可依请求内容（如中性会话标识
     * {@link ChatRequest#conversationId()}）追加自定义头——opencode 网关在其
     * {@code OpenCodeGatewayProvider} 子类中把 conversationId 编码为 x-opencode-session。
     * 通用 OpenAI 兼容端点无需覆盖本方法。
     *
     * @param builder 待发送的请求构造器（Authorization/Content-Type 已设）
     * @param request 请求，含会话标识等通用字段
     */
    protected void applyVendorHeaders(HttpRequest.Builder builder, ChatRequest request) {
        // 默认不追加厂商头
    }

    /**
     * 逐行解析 SSE 流，累加正文/思考/工具调用片段，结尾回调完整响应。
     * <p>SSE 每行以 "data:" 开头；[DONE] 表示结束。思考内容与正文各用一个累加器，
     * 供最终拼装完整 ChatResponse；工具调用按 index 分段（一个调用被切成多片），
     * 用 TreeMap 按 index 归并后再按序拼装。任一行损坏即抛 MyccException 由外层转 onError。
     */
    private void parseStream(InputStream input, StreamSink sink) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        // index -> 工具调用累加器；TreeMap 保证按 index 有序输出。
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
                // 每个 SSE data 通常含单个 choice，其 delta 为本次增量。
                JsonNode delta = choices.get(0).path("delta");
                String deltaReasoningContent = delta.path("reasoning_content").asText(null);
                String deltaContent = delta.path("content").asText(null);
                if (deltaReasoningContent != null){
                    reasoningContent.append(deltaReasoningContent);
                }
                if (deltaContent != null) {
                    content.append(deltaContent);
                }
                if (deltaReasoningContent != null || deltaContent != null) {
                    sink.onChunk(new StreamChunk(deltaContent, deltaReasoningContent));
                }
                for (JsonNode toolCall : delta.path("tool_calls")) {
                    int index = toolCall.path("index").asInt();
                    ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(index, i -> new ToolCallBuilder());
                    // id/name 通常只在首个分片出现，后续分片仅含 arguments 增量，
                    // 故需判空累加。
                    if (toolCall.has("id") && !toolCall.get("id").isNull()) {
                        builder.id = toolCall.path("id").asText();
                    }
                    JsonNode function = toolCall.path("function");
                    if (function.has("name") && !function.get("name").isNull()) {
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
        sink.onComplete(new ChatResponse(content.toString(), reasoningContent.toString(), toolCalls));
    }

    /** 单个工具调用的流式累加器：id/name 首片赋值，arguments 逐片拼接。 */
    private static final class ToolCallBuilder {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }
}
