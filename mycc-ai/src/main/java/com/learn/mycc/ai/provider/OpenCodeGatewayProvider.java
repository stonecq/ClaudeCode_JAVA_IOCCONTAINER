package com.learn.mycc.ai.provider;

import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ModelConfig;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;

/**
 * opencode.ai/go 网关专用 Provider：OpenAI 兼容请求还需带会话路由头。
 * <p>opencode 网关按 {@code x-opencode-session} 路由/限流（缺失返回 400 MissingSessionID）；
 * 值取请求的中性会话标识 {@link ChatRequest#conversationId()}——由调用方（agent 循环）每轮
 * 携带当前会话 id，同一对话线程（含多轮工具循环、续聊）沿用同一 id。无会话标识时不加头。</p>
 * <p>作为 {@link OpenAiCompatProvider} 子类只追加厂商头，不重复任何 OpenAI 兼容逻辑；
 * 换用其它 OpenAI 兼容端点直接用基类即可（此头是 opencode 独有约定）。</p>
 */
public final class OpenCodeGatewayProvider extends OpenAiCompatProvider {

    /** opencode 网关要求的会话路由头名。 */
    private static final String SESSION_HEADER = "x-opencode-session";

    /** 使用默认 HttpClient 构造，便于生产直接使用。 */
    public OpenCodeGatewayProvider(ModelConfig config) {
        super(config);
    }

    /** 包私有构造：注入自定义 HttpClient，供测试替换网络层。 */
    OpenCodeGatewayProvider(ModelConfig config, HttpClient client) {
        super(config, client);
    }

    @Override
    protected void applyVendorHeaders(HttpRequest.Builder builder, ChatRequest request) {
        String conversationId = request.conversationId();
        if (conversationId != null && !conversationId.isBlank()) {
            builder.header(SESSION_HEADER, conversationId);
        }
    }
}