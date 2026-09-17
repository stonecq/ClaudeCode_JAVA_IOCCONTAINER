package com.learn.mycc.web;

import com.learn.mycc.ui.AgentApi;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Web REST/SSE 控制器：只依赖 agent 门面 {@link AgentApi} 与本 UI 的输出端口 {@link WebPort}，
 * 不接触 agent 内部 bean 或容器。发消息异步跑一轮（事件经 WebPort → SSE），请求即时返回 202。
 */
@RestController
public class WebController {

    private final AgentApi agent;
    private final WebPort port;

    public WebController(AgentApi agent, WebPort port) {
        this.agent = agent;
        this.port = port;
    }

    /** 会话列表（标题/时间，最新在前）。 */
    @GetMapping("/v1/sessions")
    public Object listSessions() {
        return agent.listSessions();
    }

    /** 新建空会话，返回其 id。 */
    @PostMapping("/v1/sessions")
    public Map<String, String> createSession() {
        return Map.of("id", agent.createSession());
    }

    /** 某会话的历史消息（供前端渲染历史）。 */
    @GetMapping("/v1/sessions/{id}")
    public Object sessionMessages(@PathVariable String id) {
        return agent.history(id);
    }

    /** 删除会话。 */
    @DeleteMapping("/v1/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable String id) {
        agent.deleteSession(id);
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
        Thread worker = new Thread(() -> {
            try {
                agent.chat(id, text);
            } catch (Exception ignored) {
                // chat 内部已兜底异常；此处仅防线程逃逸
            }
        }, "web-agent-" + id);
        worker.setDaemon(true);
        worker.start();
    }
}