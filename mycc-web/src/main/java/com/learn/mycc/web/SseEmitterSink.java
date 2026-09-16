package com.learn.mycc.web;

import com.learn.mycc.ui.OutputEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用 Spring {@link SseEmitter} 实现的订阅者：把 {@link OutputEvent} 作为 SSE 数据帧下发。
 * 客户端以 {@code EventSource} 接收，{@code data} 为事件的 JSON（含 type/payload/sessionId/seq）。
 */
final class SseEmitterSink implements SseSink {

    private final SseEmitter emitter;

    SseEmitterSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(OutputEvent event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            // 连接已断：忽略；注销由 emitter 的完成/异常回调负责
        }
    }

    /** 主动结束该 SSE 连接。 */
    void complete() {
        emitter.complete();
    }
}