package com.learn.mycc.agent.api;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.loop.SessionReplayer;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.MessageView;
import com.learn.mycc.ui.SessionView;
import com.learn.mycc.ui.ToolView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link AgentApi} 默认实现：agent 侧组件，UI 不直接使用本类（只经接口）。
 * <p>会话与对话无状态；每会话缓存一个 {@link AgentLoop}（prototype，首次装配后复用）。
 * 事件输出取容器里登记的 {@link InteractionPort}（由 UI 提供）。</p>
 */
@Component
public class DefaultAgentApi implements AgentApi {

    private final SessionStore sessions;
    private final ToolRegistry tools;
    private final ConfigService config;
    private final IocContainer container;
    /** 每会话缓存一个循环，避免每轮重建（切换/删除会话时清理）。 */
    private final Map<String, AgentLoop> loopsBySession = new ConcurrentHashMap<>();

    @Inject
    public DefaultAgentApi(SessionStore sessions, ToolRegistry tools, ConfigService config, IocContainer container) {
        this.sessions = sessions;
        this.tools = tools;
        this.config = config;
        this.container = container;
    }

    @Override
    public List<SessionView> listSessions() {
        return sessions.list().stream()
                .map(summary -> new SessionView(summary.id(), summary.title(), summary.lastModified()))
                .toList();
    }

    @Override
    public String createSession() {
        Session session = Session.create();
        sessions.save(session);
        return session.id();
    }

    @Override
    public void deleteSession(String id) {
        sessions.delete(id);
        loopsBySession.remove(id);
    }

    @Override
    public List<MessageView> history(String id) {
        return sessions.load(id)
                .map(session -> session.conversation().messages().stream()
                        .map(message -> new MessageView(message.role().name(), message.content()))
                        .toList())
                .orElse(List.of());
    }

    @Override
    public void replay(String id) {
        InteractionPort port = container.getBean(InteractionPort.class);
        sessions.load(id).ifPresent(session -> SessionReplayer.replay(session, port));
    }

    @Override
    public void chat(String sessionId, String userMessage) {
        Session session = sessions.load(sessionId).orElseGet(() -> new Session(sessionId));
        AgentLoop loop = loopsBySession.computeIfAbsent(sessionId,
                key -> container.getBean(AgentLoop.class, session));
        loop.run(userMessage);
    }

    @Override
    public List<ToolView> listTools() {
        return tools.getAll().stream()
                .map(tool -> new ToolView(tool.getName(), tool.getDescription()))
                .toList();
    }

    @Override
    public String config(String key) {
        return config.get(key).orElse("");
    }
}