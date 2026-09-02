package com.learn.mycc.ai.model;

/**
 * LLM 采样选项：temperature 控制随机性（0~2 常用，越高越发散），
 * maxTokens 限制单次生成的最大 token 数。两者均为 null 表示交给服务端默认值。
 */
public record LlmOptions(Double temperature, Integer maxTokens) {

    /** 全字段默认（null），即完全使用服务端默认采样参数。 */
    public static LlmOptions defaults() {
        return new LlmOptions(null, null);
    }
}
