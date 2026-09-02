package com.learn.mycc.ui;

/**
 * 输出事件：type/payload/sessionId/seq，v1 CLI 与 v2 SSE 的公共协议。
 *
 * <p>设计思路：以不可变 record 作为 agent 与 UI 之间的传输单位，统一携带事件
 * 类型、文本负载、会话归属与全局序号，任何 UI 实现都可据此渲染。seq 单调递增
 * 供 UI 判断顺序/去重。</p>
 *
 * @param type      事件类型，见 {@link OutputEventType}，决定 UI 如何渲染本次事件
 * @param payload   事件文本负载：TOKEN 为流式正文，THINKING 为思考内容，ERROR 为错误信息等。
 *                  可为 null，表示该类型无需附加文本
 * @param sessionId 所属会话的标识，同一会话内一致，供多会话 UI 分流
 * @param seq       事件全局序号，从 0 起递增，用于 UI 侧排序与去重
 */
public record OutputEvent(OutputEventType type, String payload, String sessionId, long seq) {
}
