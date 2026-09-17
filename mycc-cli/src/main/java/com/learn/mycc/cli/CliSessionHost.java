package com.learn.mycc.cli;

import com.learn.mycc.storage.config.ConfigDefaults;
import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.SessionView;
import com.learn.mycc.ui.ToolView;

import java.time.Instant;
import java.util.List;

/**
 * CLI 会话宿主：持有"当前会话 id"，经 {@link AgentApi}（agent 门面）驱动对话与斜杠命令。
 * <p>不接触 agent 内部 bean 与容器——一切经门面。命令：{@code /sessions}、{@code /resume [id]}
 * （无 id 续最近，即列表首个）、{@code /tools}、{@code /config}。</p>
 */
public final class CliSessionHost implements ReplLoop.Host {

    private final AgentApi agent;
    private final CliPort port;
    private String currentId;

    public CliSessionHost(AgentApi agent, CliPort port, String initialSessionId) {
        this.agent = agent;
        this.port = port;
        this.currentId = initialSessionId;
    }

    @Override
    public String sessionId() {
        return currentId;
    }

    @Override
    public void runTurn(String userMessage) {
        agent.chat(currentId, userMessage);
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
        List<SessionView> sessions = agent.listSessions();
        if (sessions.isEmpty()) {
            println("（暂无历史会话）");
            return;
        }
        for (SessionView session : sessions) {
            println(session.id() + "\t"
                    + Instant.ofEpochMilli(session.lastModified()) + "\t" + session.title());
        }
    }

    private void resume(String id) {
        List<SessionView> sessions = agent.listSessions();
        boolean latest = id == null || id.isBlank();
        String target = latest ? (sessions.isEmpty() ? null : sessions.get(0).id()) : id;
        if (target == null) {
            println("没有历史会话");
            return;
        }
        if (sessions.stream().noneMatch(session -> session.id().equals(target))) {
            println("找不到会话 " + target);
            return;
        }
        currentId = target;
        agent.replay(target);
        println("已切换到会话 " + target);
    }

    private void listTools() {
        for (ToolView tool : agent.listTools()) {
            println(tool.name() + "\t" + tool.description());
        }
    }

    private void showConfig() {
        println(ConfigDefaults.CLI_SHOW_REASONING + " = " + agent.config(ConfigDefaults.CLI_SHOW_REASONING));
    }

    private void println(String text) {
        port.writer().println(text);
        port.writer().flush();
    }
}