package com.learn.mycc.agent.api;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.MessageView;
import com.learn.mycc.ui.SessionView;
import com.learn.mycc.ui.ToolView;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final IocContainer container;
    private final InteractionPort port;
    /** 每会话缓存一个循环，避免每轮重建（切换/删除会话时清理）。 */
    private final Map<String, AgentLoop> loopsBySession = new ConcurrentHashMap<>();

    @Inject
    public DefaultAgentApi(SessionStore sessions, ToolRegistry tools, IocContainer container, InteractionPort port) {
        this.sessions = sessions;
        this.tools = tools;
        this.container = container;
        this.port = port;
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
                .map(session -> toViews(session.conversation().messages()))
                .orElse(List.of());
    }

    /**
     * 把会话消息转成 UI 视图：工具调用与其结果**配对**输出（TOOL_CALL 紧随其 TOOL 结果），
     * 因此原始 TOOL 消息不再单列（已随调用消费）。
     */
    private static List<MessageView> toViews(List<Message> messages) {
        Map<String, String> resultByCallId = new HashMap<>();
        for (Message message : messages) {
            if (message.role() == ChatMessage.Role.TOOL && message.toolCallId() != null) {
                resultByCallId.put(message.toolCallId(), message.content());
            }
        }
        List<MessageView> views = new ArrayList<>();
        for (Message message : messages) {
            switch (message.role()) {
                case ASSISTANT -> {
                    if (message.content() != null && !message.content().isBlank()) {
                        views.add(new MessageView("ASSISTANT", message.content()));
                    }
                    for (ToolCall call : message.toolCalls()) {
                        views.add(new MessageView("TOOL_CALL", call.name() + "(" + call.arguments() + ")"));
                        String result = resultByCallId.get(call.id());
                        if (result != null) {
                            views.add(new MessageView("TOOL", result));
                        }
                    }
                }
                case TOOL -> {
                    // 结果已随其调用配对输出，跳过原 TOOL 消息
                }
                default -> views.add(new MessageView(message.role().name(), message.content()));
            }
        }
        return views;
    }


    @Override
    public void chat(String sessionId, String userMessage) {
        Session session = sessions.load(sessionId).orElseGet(() -> new Session(sessionId));
        AgentLoop loop = loopsBySession.computeIfAbsent(sessionId,
                key -> container.getBean(AgentLoop.class, port, session));
        loop.run(userMessage);
    }

    @Override
    public List<ToolView> listTools() {
        return tools.getAll().stream()
                .map(tool -> new ToolView(tool.getName(), tool.getDescription()))
                .toList();
    }
}