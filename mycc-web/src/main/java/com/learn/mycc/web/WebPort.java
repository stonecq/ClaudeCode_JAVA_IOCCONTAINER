package com.learn.mycc.web;

import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web 端交互端口：把 agent 下发的 {@link OutputEvent} 按 {@code sessionId} 路由给已订阅的
 * SSE 连接。agent 侧只认 {@link InteractionPort}，本类与 {@code CliPort} 平级——换 UI 不改 agent。
 *
 * <p>多会话隔离：每个会话一组订阅者；无订阅者的事件被丢弃（Web 无人在线时无需缓冲）。
 * 同会话说多个标签订阅都能收到；不同会话互不串流。</p>
 *
 * <p><b>刻意不加 {@code @Component}</b>：容器里 {@code CliPort}（CLI 默认）已实现
 * {@link InteractionPort}，若两者都按类型注册会多候选。故由 {@code Main} 在 {@code start()}
 * 之前、按启动模式把本类注册为 {@code InteractionPort}（web 模式）——保证启动前唯一确定，
 * 运行期不再注册/覆盖。</p>
 */
public class WebPort implements InteractionPort {

    /** 会话 id → 该会话的 SSE 订阅者集合（并发安全）。 */
    private final Map<String, Set<SseSink>> sinksBySession = new ConcurrentHashMap<>();

    /** 注册某会话的 SSE 订阅者（连接建立时）。 */
    public void register(String sessionId, SseSink sink) {
        sinksBySession.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(sink);
    }

    /** 注销订阅者（连接关闭时）；会话无订阅者后条目可保留，影响可忽略。 */
    public void unregister(String sessionId, SseSink sink) {
        Set<SseSink> sinks = sinksBySession.get(sessionId);
        if (sinks != null) {
            sinks.remove(sink);
        }
    }

    @Override
    public void onEvent(OutputEvent event) {
        Set<SseSink> sinks = sinksBySession.get(event.sessionId());
        if (sinks == null) {
            return;
        }
        for (SseSink sink : sinks) {
            sink.send(event);
        }
    }
}