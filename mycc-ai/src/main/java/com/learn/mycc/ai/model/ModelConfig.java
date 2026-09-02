package com.learn.mycc.ai.model;

/**
 * Provider 连接配置：API Key 与接口基址。
 * <p>apiKey 用于 Bearer 认证，baseUrl 为 OpenAI 兼容接口根地址（不含 /chat/completions 后缀，
 * 由 Provider 拼接）。两者均不应为 null；apiKey 属敏感信息，严禁打印或写入日志/代码库。
 */
public record ModelConfig(String apiKey, String baseUrl) {
}
