package com.learn.mycc.ai.spi;

import com.learn.mycc.ai.model.ChatRequest;

/**
 * LLM 提供方接口：接收请求，通过 {@link StreamSink} 流式回传结果。
 * <p>SPI 抽象层，屏蔽底层差异：MockProvider（离线模拟）与 OpenAiCompatProvider（真实 HTTP）
 * 均实现此接口，上层 agent 循环只依赖本接口，实现可插拔替换。
 */
public interface LlmProvider {

    /**
     * 发起一次对话。
     * @param request 请求（模型、消息历史、工具定义、采样选项），不应为 null
     * @param sink    流式回调：生成中回调 onChunk，成功后回调 onComplete，失败回调 onError
     */
    void chat(ChatRequest request, StreamSink sink);
}
