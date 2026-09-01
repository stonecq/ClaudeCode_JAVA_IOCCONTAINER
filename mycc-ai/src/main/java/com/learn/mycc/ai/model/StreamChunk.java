package com.learn.mycc.ai.model;

/** 流式输出片段：增量文本内容。 */
public record StreamChunk(String content, String reasoningContent) {
    public StreamChunk(String content){
        this(content, null);
    }
}
