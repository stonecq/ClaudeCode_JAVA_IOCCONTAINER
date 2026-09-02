package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.RecordingPort;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsSessionAfterRun() {
        FileStorage fileStorage = new FileStorage(tempDir);
        SessionStore store = new SessionStore(fileStorage);
        MockProvider provider = MockProvider.scripted(request -> ChatResponse.text("回答"));
        RecordingPort port = new RecordingPort();

        Session session = Session.create();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, new ToolRegistry(), "mock", 10, store, session);
        agent.run("你好");

        Session saved = store.latest().orElseThrow();
        assertThat(saved.id()).isEqualTo(agent.session().id());
        assertThat(saved.conversation().messages()).extracting(Message::content)
                .containsExactly("你好", "回答");
        assertThat(fileStorage.read("session/latest")).contains(agent.session().id());
    }

    @Test
    void continuesInExplicitSelectedSession() {
        FileStorage fileStorage = new FileStorage(tempDir);
        SessionStore store = new SessionStore(fileStorage);
        Session old = Session.create();
        old.addMessage(Message.user("旧问题"));
        old.addMessage(Message.assistant("旧回答", List.of()));
        store.save(old);

        AtomicReference<List<ChatMessage>> seen = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            seen.set(request.messages());
            return ChatResponse.text("新回答");
        });
        RecordingPort port = new RecordingPort();

        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, new ToolRegistry(), "mock", 10, store, old);

        assertThat(agent.session().id()).isEqualTo(old.id());
        assertThat(agent.session().conversation().messages()).hasSize(2);

        String result = agent.run("新问题");

        assertThat(result).isEqualTo("新回答");
        assertThat(seen.get()).extracting(ChatMessage::content)
                .containsExactly("旧问题", "旧回答", "新问题");
        assertThat(agent.session().conversation().messages()).hasSize(4);
        assertThat(store.latest().orElseThrow().conversation().messages()).hasSize(4);
    }

    @Test
    void bindsExplicitSessionEvenWhenLatestExists() {
        FileStorage fileStorage = new FileStorage(tempDir);
        SessionStore store = new SessionStore(fileStorage);
        Session old = Session.create();
        old.addMessage(Message.user("旧问题"));
        old.addMessage(Message.assistant("旧回答", List.of()));
        store.save(old);

        Session fresh = Session.create();
        MockProvider provider = MockProvider.scripted(request -> ChatResponse.text("回答"));
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, new ToolRegistry(), "mock", 10, store, fresh);
        agent.run("新问题");

        assertThat(agent.session().id()).isEqualTo(fresh.id());
        assertThat(agent.session().conversation().messages()).extracting(Message::content)
                .containsExactly("新问题", "回答");
    }

    @Test
    void withoutStorageDoesNotPersist() {
        FileStorage fileStorage = new FileStorage(tempDir);
        MockProvider provider = MockProvider.scripted(request -> ChatResponse.text("回答"));
        RecordingPort port = new RecordingPort();

        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, new ToolRegistry(), "mock", 10);
        agent.run("你好");

        assertThat(fileStorage.keys()).isEmpty();
    }
}
