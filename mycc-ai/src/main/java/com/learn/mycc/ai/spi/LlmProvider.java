package com.learn.mycc.ai.spi;

import com.learn.mycc.ai.model.ChatRequest;

/** LLM 提供方接口：接收请求，通过 {@link StreamSink} 流式回传结果。 */
public interface LlmProvider {

    void chat(ChatRequest request, StreamSink sink);
}
