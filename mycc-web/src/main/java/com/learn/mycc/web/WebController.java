package com.learn.mycc.web;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.core.context.IocContainer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Web REST/SSE 控制器：会话增删查 + 发消息 + SSE 订阅。
 * <p>依赖自研容器 {@link IocContainer}：取 {@code SessionStore}/{@code WebPort}，并按会话装配
 * {@link AgentLoop}。发消息时异步跑一轮 agent（事件经 {@code WebPort} → SSE），请求即时返回 202。</p>
 */
@RestController
public class WebController {

    private final IocContainer container;
    private final SessionStore sessions;
    private final WebPort port;

    public WebController(IocContainer container) {
        this.container = container;
        this.sessions = container.getBean(SessionStore.class);
        // WebPort 由 Main 在 start 前注册进容器，这里取同一实例（SSE 订阅表）
        this.port = container.getBean(WebPort.class);
    }

    /** 会话列表（标题/时间，最新在前）。 */
    @GetMapping("/v1/sessions")
    public Object listSessions() {
        return sessions.list();
    }

    /** 新建空会话，返回其 id。 */
    @PostMapping("/v1/sessions")
    public Map<String, String> createSession() {
        Session session = Session.create();
        sessions.save(session);
        return Map.of("id", session.id());
    }

    /** 某会话的历史消息（供前端渲染历史）。 */
    @GetMapping("/v1/sessions/{id}")
    public Object sessionMessages(@PathVariable String id) {
        return sessions.load(id)
                .map(session -> session.conversation().messages())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: " + id));
    }

    /** 删除会话。 */
    @DeleteMapping("/v1/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable String id) {
        sessions.delete(id);
    }

    /** 订阅某会话的输出事件（SSE 长连，永不超时）。 */
    @GetMapping("/v1/sessions/{id}/events")
    public SseEmitter events(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(0L);
        SseEmitterSink sink = new SseEmitterSink(emitter);
        port.register(id, sink);
        emitter.onCompletion(() -> port.unregister(id, sink));
        emitter.onTimeout(() -> port.unregister(id, sink));
        emitter.onError(error -> port.unregister(id, sink));
        return emitter;
    }

    /** 发送一条用户消息：异步跑一轮 agent，事件经 SSE 推送；立即返回 202。 */
    @PostMapping("/v1/sessions/{id}/message")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendMessage(@PathVariable String id, @RequestBody String text) {
        Session session = sessions.load(id).orElseGet(() -> new Session(id));
        AgentLoop agent = container.getBean(AgentLoop.class, session);
        Thread worker = new Thread(() -> {
            try {
                agent.run(text);
            } catch (Exception ignored) {
                // run 内部已兜底异常；此处仅防线程逃逸
            }
        }, "web-agent-" + id);
        worker.setDaemon(true);
        worker.start();
    }
}