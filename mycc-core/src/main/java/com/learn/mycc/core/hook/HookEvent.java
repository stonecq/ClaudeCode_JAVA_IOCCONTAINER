package com.learn.mycc.core.hook;

/**
 * 钩子事件：派发给订阅者的事件载体（不可变 record）。
 * 携带事件类型、所属会话标识与可选文本负载，供钩子按上下文做判断。
 */
public record HookEvent(HookEventType type, String sessionId, Object payload) {
}
