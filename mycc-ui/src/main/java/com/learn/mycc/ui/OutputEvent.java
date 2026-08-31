package com.learn.mycc.ui;

/** 输出事件：type/payload/sessionId/seq，v1 CLI 与 v2 SSE 的公共协议。 */
public record OutputEvent(OutputEventType type, String payload, String sessionId, long seq) {
}
