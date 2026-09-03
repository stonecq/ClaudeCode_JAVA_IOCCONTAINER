package com.learn.mycc.agent.storage;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndRestoresSessionVerbatim() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session original = Session.create();
        original.addMessage(Message.user("写一个 demo.txt"));
        original.addMessage(Message.assistant("", List.of(new ToolCall("call-1", "write_file", "{\"path\":\"demo.txt\"}"))));
        original.addMessage(Message.tool("call-1", "ok"));
        original.addMessage(Message.assistant("已写入 demo.txt", List.of()));

        store.save(original);

        Session restored = store.load(original.id()).orElseThrow();
        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.conversation().messages())
                .containsExactlyElementsOf(original.conversation().messages());
    }

    @Test
    void writesJsonUnderSessionDirectory() throws IOException {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.user("hi"));

        store.save(session);

        Path file = tempDir.resolve("session").resolve(session.id() + ".json");
        assertThat(Files.isRegularFile(file)).isTrue();
        assertThat(Files.readString(file)).contains("hi").contains("USER");
    }

    @Test
    void loadMissingIdReturnsEmpty() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        assertThat(store.load("no-such-id")).isEmpty();
    }

    @Test
    void restoredSessionStillConvertsToChatMessages() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session original = Session.create();
        original.addMessage(Message.user("read a file"));
        original.addMessage(Message.assistant("", List.of(new ToolCall("c1", "read_file", "{\"path\":\"a.txt\"}"))));
        original.addMessage(Message.tool("c1", "a.txt 内容"));
        store.save(original);

        Session restored = store.load(original.id()).orElseThrow();

        assertThat(restored.conversation().toChatMessages()).hasSize(3);
        assertThat(restored.conversation().toChatMessages().get(1).hasToolCalls()).isTrue();
        assertThat(restored.conversation().toChatMessages().get(1).toolCalls().get(0).name()).isEqualTo("read_file");
        assertThat(restored.conversation().toChatMessages().get(2).toolCallId()).isEqualTo("c1");
    }

    @Test
    void roundTripPreservesEmptyToolCalls() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session original = Session.create();
        original.addMessage(Message.assistant("plain answer", List.of()));

        store.save(original);
        Session restored = store.load(original.id()).orElseThrow();

        assertThat(restored.conversation().messages().get(0).toolCalls()).isEmpty();
    }

    @Test
    void latestEmptyWhenNothingSaved() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        assertThat(store.latest()).isEmpty();
    }

    @Test
    void latestReturnsMostRecentlySavedSession() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session first = Session.create();
        first.addMessage(Message.user("first"));
        store.save(first);
        Session second = Session.create();
        second.addMessage(Message.user("second"));
        store.save(second);

        Session latest = store.latest().orElseThrow();
        assertThat(latest.id()).isEqualTo(second.id());
        assertThat(latest.conversation().messages()).extracting(Message::content)
                .containsExactly("second");
    }

    @Test
    void saveWritesLatestPointer() throws IOException {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.user("hi"));
        store.save(session);

        assertThat(Files.readString(tempDir.resolve("session/latest"))).isEqualTo(session.id());
    }

    @Test
    void listReturnsSessionsOrderedByLastModifiedDesc() throws IOException {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session first = Session.create();
        first.addMessage(Message.user("first question"));
        store.save(first);
        Session second = Session.create();
        second.addMessage(Message.user("second question"));
        store.save(second);

        Files.setLastModifiedTime(tempDir.resolve("session").resolve(first.id() + ".json"), FileTime.fromMillis(1000L));
        Files.setLastModifiedTime(tempDir.resolve("session").resolve(second.id() + ".json"), FileTime.fromMillis(2000L));

        List<SessionStore.SessionSummary> list = store.list();
        assertThat(list).extracting(SessionStore.SessionSummary::id)
                .containsExactly(second.id(), first.id());
        assertThat(list).extracting(SessionStore.SessionSummary::title)
                .containsExactly("second question", "first question");
    }

    @Test
    void listTitleFallsBackToPlaceholderWithoutUserMessage() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.assistant("hello", List.of()));
        store.save(session);

        assertThat(store.list()).extracting(SessionStore.SessionSummary::title)
                .containsExactly("（空对话）");
    }

    @Test
    void listExcludesLatestPointerAndEmptyWhenNothingSaved() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        assertThat(store.list()).isEmpty();

        Session session = Session.create();
        session.addMessage(Message.user("hi"));
        store.save(session);

        // save 会同时写出 session/latest 指针；不带 .json 后缀，不应入列
        assertThat(store.list()).hasSize(1);
    }

    @Test
    void listTitleUsesLastUserMessageNotFirst() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.user("第一问"));
        session.addMessage(Message.assistant("答一", List.of()));
        session.addMessage(Message.user("第二问"));
        store.save(session);

        assertThat(store.list()).extracting(SessionStore.SessionSummary::title)
                .containsExactly("第二问");
    }

    @Test
    void listSummaryCarriesLastModifiedTimestamp() throws IOException {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.user("hi"));
        store.save(session);
        Files.setLastModifiedTime(tempDir.resolve("session").resolve(session.id() + ".json"), FileTime.fromMillis(1234L));

        SessionStore.SessionSummary summary = store.list().get(0);
        assertThat(summary.lastModified()).isEqualTo(1234L);
    }
}
