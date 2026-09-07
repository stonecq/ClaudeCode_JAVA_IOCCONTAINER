package com.learn.mycc.ai.provider;

import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.ai.spi.StreamSink;
import com.learn.mycc.core.exception.MyccException;

/**
 * LLM 不可用兜底实现（fail-fast）：缺 OPENCODE_KEY 装配时替代真实 provider。
 * chat 直接抛 {@code MyccException} 引导用户配置 key，而非 NPE 或假数据；
 * 由 agent 循环统一捕获并以 ERROR 事件回显。
 */
public final class UnavailableLlmProvider implements LlmProvider {

    @Override
    public void chat(ChatRequest request, StreamSink sink) {
        throw new MyccException("未设置环境变量 OPENCODE_KEY，无法接入 openCode");
    }
}