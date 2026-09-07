package com.learn.mycc.ai.config;

import com.learn.mycc.ai.model.ModelConfig;
import com.learn.mycc.ai.provider.OpenAiCompatProvider;
import com.learn.mycc.ai.provider.UnavailableLlmProvider;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.core.annotation.Bean;
import com.learn.mycc.core.annotation.Configuration;
import com.learn.mycc.core.annotation.Named;

/**
 * AI 装配配置：LLM 提供方以 @Bean 工厂方法交给容器纳管。
 * 依据环境变量 OPENCODE_KEY 构造真实 provider；缺 key 时回落
 * {@link UnavailableLlmProvider} 兜底（chat 抛错而非 NPE），引导信息由装配根
 * Main 对聊天命令缺 key 预检输出并退出非 0，只读命令免 key。
 */
@Configuration
public class AiConfig {

    @Bean
    public LlmProvider openCodeLlmProvider() {
        String baseUrl = "https://opencode.ai/zen/go/v1";
        String apiKey = System.getenv("OPENCODE_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return new UnavailableLlmProvider();
        }
        return new OpenAiCompatProvider(new ModelConfig(apiKey, baseUrl));
    }
}