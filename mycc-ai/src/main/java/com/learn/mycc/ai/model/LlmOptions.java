package com.learn.mycc.ai.model;

/** LLM 采样选项：null 表示交给服务端默认。 */
public record LlmOptions(Double temperature, Integer maxTokens) {

    public static LlmOptions defaults() {
        return new LlmOptions(null, null);
    }
}
