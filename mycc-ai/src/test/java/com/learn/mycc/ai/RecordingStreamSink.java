package com.learn.mycc.ai;

import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.StreamChunk;
import com.learn.mycc.ai.spi.StreamSink;

import java.util.ArrayList;
import java.util.List;

/** 测试用 StreamSink：记录所有 chunk、完整响应与错误。 */
public final class RecordingStreamSink implements StreamSink {

    public final List<StreamChunk> chunks = new ArrayList<>();
    public ChatResponse response;
    public Throwable error;

    @Override
    public void onChunk(StreamChunk chunk) {
        chunks.add(chunk);
    }

    @Override
    public void onComplete(ChatResponse response) {
        this.response = response;
    }

    @Override
    public void onError(Throwable error) {
        this.error = error;
    }
}
