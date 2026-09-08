package com.learn.mycc.ai.config;

import com.learn.mycc.ai.model.ModelConfig;
import com.learn.mycc.ai.provider.OpenCodeGatewayProvider;
import com.learn.mycc.ai.provider.UnavailableLlmProvider;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.core.annotation.Bean;
import com.learn.mycc.core.annotation.Configuration;

/**
 * AI 装配配置：LLM 提供方以 @Bean 工厂方法交给容器纳管。
 * 依据环境变量 OPENCODE_KEY 构造 opencode 网关 provider；缺 key 时回落
 * {@link UnavailableLlmProvider} 兜底（chat 抛错而非 NPE），引导信息由装配根
 * Main 对聊天命令缺 key 预检输出并退出非 0，只读命令免 key。
 * <p>换用其它 OpenAI 兼容端点只需改本类返回基类 {@code OpenAiCompatProvider}（配 baseUrl）；
 * opencode 独有的 {@code x-opencode-session} 头由其子类 {@link OpenCodeGatewayProvider} 注入，
 * 会话 id 随每次请求由 AgentLoop 携带（见 ChatRequest.conversationId），核心不感知厂商约定。</p>
 */
@Configuration
public class AiConfig {

    /** opencode 兼容网关的接口基址（不含 /chat/completions 后缀，由 Provider 拼接）。 */
    private static final String BASE_URL = "https://opencode.ai/zen/go/v1";

    /** 按 OPENCODE_KEY 装配 opencode 网关 provider；key 只存在于进程环境，不写入代码与日志。 */
    @Bean
    public LlmProvider llmProvider() {
        String apiKey = System.getenv("OPENCODE_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return new UnavailableLlmProvider();
        }
        return new OpenCodeGatewayProvider(new ModelConfig(apiKey, BASE_URL));
    }
}