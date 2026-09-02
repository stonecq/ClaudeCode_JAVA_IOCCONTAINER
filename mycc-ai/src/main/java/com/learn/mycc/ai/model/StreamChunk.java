package com.learn.mycc.ai.model;

/**
 * 流式输出片段：增量文本内容。
 * <p>每次 {@code StreamSink.onChunk} 携带一小段增量（delta），由接收方不断累加。
 * content 为正文增量、reasoningContent 为思考增量，二者均可为空串或 null
 * （某个时刻可能只有其一，例如思考阶段 content 为 null）。
 */
public record StreamChunk(String content, String reasoningContent) {

    /** 构造仅含正文增量的片段（reasoningContent 置 null）。 */
    public StreamChunk(String content){
        this(content, null);
    }
}
