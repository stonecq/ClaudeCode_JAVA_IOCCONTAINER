package com.learn.mycc.app;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMenuTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    private final String nl = System.lineSeparator();

    private SessionStore storeWith(String title) {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.user(title));
        store.save(session);
        return store;
    }

    private static BufferedReader reader(CharSequence input) {
        return new BufferedReader(new StringReader(input.toString()));
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void selectsExistingSessionByIndex() throws Exception {
        SessionMenu menu = new SessionMenu(storeWith("第一段对话"), out, reader("1\n"));

        Session session = menu.select();

        assertThat(session.conversation().messages()).extracting(Message::content)
                .containsExactly("第一段对话");
    }

    @Test
    void selectsNewConversationOption() throws Exception {
        SessionStore store = storeWith("一段历史");
        SessionMenu menu = new SessionMenu(store, out, reader("2\n"));

        Session session = menu.select();

        assertThat(session.isEmpty()).isTrue();
    }

    @Test
    void repromptsOnInvalidInputThenSucceeds() throws Exception {
        SessionMenu menu = new SessionMenu(storeWith("历史"), out, reader("abc\n2\n"));

        Session session = menu.select();

        assertThat(session.isEmpty()).isTrue();
        assertThat(output()).contains("无效选择");
    }

    @Test
    void returnsFreshSessionOnCtrlD() throws Exception {
        BufferedReader eof = new BufferedReader(new StringReader(""));
        SessionMenu menu = new SessionMenu(storeWith("历史"), out, eof);

        Session session = menu.select();

        assertThat(session.isEmpty()).isTrue();
    }

    @Test
    void truncatesLongTitlesWithEllipsis() throws Exception {
        SessionMenu menu = new SessionMenu(
                storeWith("abcdeabcdeabcdeabcdeabcde"), out, reader("1\n"));

        menu.select();

        assertThat(output()).contains("1. abcdeabcdeabcdeabcde…");
    }

    @Test
    void printHistoryPrintsRoleMarkers() {
        Session session = Session.create();
        session.addMessage(Message.user("读一下文件"));
        session.addMessage(Message.assistant("", List.of(new ToolCall("c1", "read_file", "{\"path\":\"a.txt\"}"))));
        session.addMessage(Message.tool("c1", "文件内容"));
        session.addMessage(Message.assistant("已读取", List.of()));

        new SessionMenu(new SessionStore(new FileStorage(tempDir)), out, reader("")).printHistory(session);

        assertThat(output()).isEqualTo(
                "我 > 读一下文件" + nl
                        + "[工具] read_file({\"path\":\"a.txt\"})" + nl
                        + "[结果] 文件内容" + nl
                        + "助手 > 已读取" + nl);
    }

    @Test
    void printHistoryForEmptySessionPrintsPlaceholder() {
        new SessionMenu(new SessionStore(new FileStorage(tempDir)), out, reader("")).printHistory(Session.create());

        assertThat(output()).isEqualTo("（新会话）" + nl);
    }
}