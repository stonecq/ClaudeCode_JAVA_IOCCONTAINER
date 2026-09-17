package com.learn.mycc.cli;

import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.MessageView;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import com.learn.mycc.ui.SessionView;
import com.learn.mycc.ui.ToolView;
import com.learn.mycc.ui.UiConfig;

import java.time.Instant;
import java.util.List;

/**
 * CLI 会话宿主：持有"当前会话 id"，经 {@link AgentApi}（agent 门面）驱动对话与斜杠命令。
 * <p>不接触 agent 内部 bean/容器；{@code /config} 显示的是 UI 侧配置（{@link UiConfig}）。
 * 命令：{@code /sessions}、{@code /resume [id]}（无 id 续最近）、{@code /tools}、{@code /config}。</p>
 */
public final class CliSessionHost implements ReplLoop.Host {

    private final AgentApi agent;
    private final CliPort port;
    private final UiConfig uiConfig;
    private String currentId;

    public CliSessionHost(AgentApi agent, CliPort port, UiConfig uiConfig, String initialSessionId) {
        this.agent = agent;
        this.port = port;
        this.uiConfig = uiConfig;
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
        renderHistory(target);
        println("已切换到会话 " + target);
    }

    /** 把某会话历史渲染到终端：复用 CliPort 的事件渲染，保持与实时对话一致。 */
    private void renderHistory(String id) {
        for (MessageView message : agent.history(id)) {
            switch (message.role()) {
                case "USER" -> port.onEvent(new OutputEvent(OutputEventType.USER, message.text(), id, 0));
                case "ASSISTANT" -> {
                    if (message.text() != null && !message.text().isBlank()) {
                        port.onEvent(new OutputEvent(OutputEventType.TOKEN, message.text(), id, 0));
                        port.onEvent(new OutputEvent(OutputEventType.DONE, null, id, 0));
                    }
                }
                case "TOOL_CALL" -> port.onEvent(new OutputEvent(OutputEventType.TOOL_CALL, message.text(), id, 0));
                case "TOOL" -> port.onEvent(new OutputEvent(OutputEventType.TOOL_RESULT, message.text(), id, 0));
                default -> {
                    // SYSTEM 等不渲染
                }
            }
        }
    }

    private void listTools() {
        for (ToolView tool : agent.listTools()) {
            println(tool.name() + "\t" + tool.description());
        }
    }

    private void showConfig() {
        println("ui.cli.showReasoning = " + uiConfig.cliShowReasoning());
    }

    private void println(String text) {
        port.writer().println(text);
        port.writer().flush();
    }
}