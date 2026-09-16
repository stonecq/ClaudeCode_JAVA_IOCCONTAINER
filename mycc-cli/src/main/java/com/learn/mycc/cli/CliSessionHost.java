package com.learn.mycc.cli;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.loop.SessionReplayer;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigDefaults;
import com.learn.mycc.storage.config.ConfigService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CLI 会话宿主：持有"当前会话"，驱动一轮对话并处理斜杠命令。
 * <p>命令：{@code /sessions}（列出）、{@code /resume [id]}（切换到指定/最近会话并回放历史）、
 * {@code /tools}（列工具）、{@code /config}（列配置）。切换会话后，后续对话绑定新会话
 * （每轮按当前会话装配 {@link AgentLoop}）。</p>
 */
public final class CliSessionHost implements ReplLoop.Host {

    private final SessionStore store;
    private final ToolRegistry registry;
    private final ConfigService config;
    private final CliPort port;
    private final IocContainer container;
    private Session current;
    /** 当前会话的循环：懒建并缓存（避免每轮新建）；切换会话时置 null 重建。 */
    private AgentLoop agent;

    public CliSessionHost(SessionStore store, ToolRegistry registry, ConfigService config,
                          CliPort port, IocContainer container, Session current) {
        this.store = store;
        this.registry = registry;
        this.config = config;
        this.port = port;
        this.container = container;
        this.current = current;
    }

    @Override
    public String sessionId() {
        return current.id();
    }

    @Override
    public void runTurn(String userMessage) {
        agent().run(userMessage);
    }

    /** 当前会话的循环：首次使用时经容器装配并缓存；切换会话后重建。 */
    private AgentLoop agent() {
        if (agent == null) {
            agent = container.getBean(AgentLoop.class, current);
        }
        return agent;
    }

    @Override
    public void handleSlashCommand(String line) {
        String[] parts = line.trim().split("\\s+", 2);
        String command = parts[0];
        String arg = parts.length > 1 ? parts[1].trim() : null;
        switch (command) {
            case "/sessions" -> listSessions();
            case "/resume" -> resume(arg);
            case "/tools" -> listTools();
            case "/config" -> showConfig();
            default -> println("未知命令: " + command
                    + "（可用 /sessions /resume /tools /config /clear /exit）");
        }
    }

    private void listSessions() {
        List<SessionStore.SessionSummary> sessions = store.list();
        if (sessions.isEmpty()) {
            println("（暂无历史会话）");
            return;
        }
        for (SessionStore.SessionSummary summary : sessions) {
            println(summary.id() + "\t"
                    + Instant.ofEpochMilli(summary.lastModified()) + "\t" + summary.title());
        }
    }

    private void resume(String id) {
        boolean latest = id == null || id.isBlank();
        Optional<Session> loaded = latest ? store.latest() : store.load(id);
        if (loaded.isEmpty()) {
            println(latest ? "没有历史会话" : "找不到会话 " + id);
            return;
        }
        current = loaded.get();
        agent = null; // 会话切换：丢弃旧循环，下次驱动时按新会话重建
        SessionReplayer.replay(current, port);
        println("已切换到会话 " + current.id());
    }

    private void listTools() {
        registry.getAll().forEach(tool -> println(tool.getName() + "\t" + tool.getDescription()));
    }

    private void showConfig() {
        println(ConfigDefaults.CLI_SHOW_REASONING + " = "
                + config.get(ConfigDefaults.CLI_SHOW_REASONING).orElse(""));
    }

    private void println(String text) {
        port.writer().println(text);
        port.writer().flush();
    }
}