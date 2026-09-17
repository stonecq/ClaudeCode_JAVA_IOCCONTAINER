package com.learn.mycc.cli;

import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.MessageView;
import com.learn.mycc.ui.SessionView;
import com.learn.mycc.ui.ToolView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CliSessionHostTest {

    StringWriter buffer;
    CliPort port;
    CliSessionHost host;
    FakeAgentApi api;

    @BeforeEach
    void setUp() {
        buffer = new StringWriter();
        port = new CliPort(new PrintWriter(buffer), false, true);
        api = new FakeAgentApi();
        host = new CliSessionHost(api, port, "s0");
    }

    @Test
    void sessionsListsSavedSessions() {
        api.sessions.put("s1", List.of(new MessageView("USER", "第一句")));

        host.handleSlashCommand("/sessions");

        assertThat(buffer.toString()).contains("s1").contains("第一句");
    }

    @Test
    void sessionsPrintsPlaceholderWhenEmpty() {
        host.handleSlashCommand("/sessions");
        assertThat(buffer.toString()).contains("（暂无历史会话）");
    }

    @Test
    void resumeSwitchesCurrentSession() {
        api.sessions.put("s1", List.of(new MessageView("USER", "历史问题")));

        host.handleSlashCommand("/resume s1");

        assertThat(host.sessionId()).isEqualTo("s1");
        assertThat(api.replayed).containsExactly("s1");
        assertThat(buffer.toString()).contains("已切换到会话 s1");
    }

    @Test
    void resumeWithoutIdPicksMostRecent() {
        api.sessions.put("newest", List.of());
        api.sessions.put("older", List.of());

        host.handleSlashCommand("/resume");

        assertThat(host.sessionId()).isEqualTo("newest"); // 列表首个 = 最新
    }

    @Test
    void resumeMissingIdReportsNotFound() {
        host.handleSlashCommand("/resume no-such");
        assertThat(buffer.toString()).contains("找不到会话 no-such");
    }

    @Test
    void resumeWithoutAnySessionReportsNoHistory() {
        host.handleSlashCommand("/resume");
        assertThat(buffer.toString()).contains("没有历史会话");
    }

    @Test
    void toolsListsRegisteredTools() {
        host.handleSlashCommand("/tools");
        assertThat(buffer.toString()).contains("demo\t示例工具");
    }

    @Test
    void configPrintsEffectiveValue() {
        host.handleSlashCommand("/config");
        assertThat(buffer.toString()).contains("cli.showReasoning = true");
    }

    @Test
    void unknownCommandGivesHint() {
        host.handleSlashCommand("/nope");
        assertThat(buffer.toString()).contains("未知命令: /nope");
    }

    @Test
    void runTurnDelegatesChatToAgentApi() {
        host.runTurn("你好");
        assertThat(api.chats).containsExactly("s0:你好");
    }

    /** 内存版 AgentApi 假实现。 */
    static final class FakeAgentApi implements AgentApi {
        final Map<String, List<MessageView>> sessions = new LinkedHashMap<>();
        final List<String> chats = new ArrayList<>();
        final List<String> replayed = new ArrayList<>();

        @Override
        public List<SessionView> listSessions() {
            return sessions.entrySet().stream()
                    .map(e -> new SessionView(e.getKey(), title(e.getValue()), 0L))
                    .toList();
        }

        @Override
        public String createSession() {
            String id = "s" + (sessions.size() + 1);
            sessions.put(id, List.of());
            return id;
        }

        @Override
        public void deleteSession(String id) {
            sessions.remove(id);
        }

        @Override
        public List<MessageView> history(String id) {
            return sessions.getOrDefault(id, List.of());
        }

        @Override
        public void replay(String id) {
            replayed.add(id);
        }

        @Override
        public void chat(String sessionId, String userMessage) {
            chats.add(sessionId + ":" + userMessage);
        }

        @Override
        public List<ToolView> listTools() {
            return List.of(new ToolView("demo", "示例工具"));
        }

        @Override
        public String config(String key) {
            return "true";
        }

        private static String title(List<MessageView> history) {
            return history.stream()
                    .filter(m -> "USER".equals(m.role()))
                    .reduce((a, b) -> b)
                    .map(MessageView::text)
                    .orElse("（空对话）");
        }
    }
}