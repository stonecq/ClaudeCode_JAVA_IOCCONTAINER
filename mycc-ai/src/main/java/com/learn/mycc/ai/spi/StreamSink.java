package com.learn.mycc.ai.spi;

import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.StreamChunk;

/** 流式输出回调：Provider 边生成边回调，最后以完整响应或错误结束。 */
public interface StreamSink {

    void onChunk(StreamChunk chunk);

    void onComplete(ChatResponse response);

    void onError(Throwable error);
}
