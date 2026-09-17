package com.learn.mycc.agent.api;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.file.FileStorage;
import com.learn.mycc.ui.MessageView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentApiTest {

    @TempDir
    Path tempDir;

    SessionStore store;
    DefaultAgentApi api;

    @BeforeEach
    void setUp() {
        store = new SessionStore(new FileStorage(tempDir));
        // history 只用 SessionStore；container/port 传 null
        api = new DefaultAgentApi(store, new ToolRegistry(), null, null);
    }

    @Test
    void historyPairsToolCallWithItsResult() {
        Session session = Session.create();
        session.addMessage(Message.assistant("", List.of(new ToolCall("c1", "read_memory", "{\"id\":\"x\"}"))));
        session.addMessage(Message.tool("c1", "记忆内容"));
        store.save(session);

        List<MessageView> views = api.history(session.id());

        assertThat(views).containsExactly(
                new MessageView("TOOL_CALL", "read_memory({\"id\":\"x\"})"),
                new MessageView("TOOL", "记忆内容"));
    }

    @Test
    void historyKeepsAssistantTextBeforeToolCalls() {
        Session session = Session.create();
        session.addMessage(Message.assistant("我先查一下", List.of(new ToolCall("c1", "read_memory", "{}"))));
        session.addMessage(Message.tool("c1", "结果"));
        store.save(session);

        assertThat(api.history(session.id())).extracting(MessageView::role)
                .containsExactly("ASSISTANT", "TOOL_CALL", "TOOL");
    }

    @Test
    void historyKeepsPlainUserAndAssistant() {
        Session session = Session.create();
        session.addMessage(Message.user("你好"));
        session.addMessage(Message.assistant("回复", List.of()));
        store.save(session);

        assertThat(api.history(session.id())).extracting(MessageView::role)
                .containsExactly("USER", "ASSISTANT");
    }
}