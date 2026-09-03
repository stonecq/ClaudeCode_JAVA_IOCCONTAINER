package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.RecordingPort;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionReplayerTest {

    @Test
    void replaysFullHistoryAsEventSequence() {
        Session session = Session.create();
        session.addMessage(Message.user("读一下文件"));
        session.addMessage(Message.assistant("", List.of(new ToolCall("c1", "read_file", "{\"path\":\"a.txt\"}"))));
        session.addMessage(Message.tool("c1", "文件内容"));
        session.addMessage(Message.assistant("已读取", List.of()));

        RecordingPort port = new RecordingPort();
        SessionReplayer.replay(session, port);

        assertThat(port.types())
                .containsExactly(OutputEventType.USER, OutputEventType.TOOL_CALL,
                        OutputEventType.TOOL_RESULT, OutputEventType.TOKEN, OutputEventType.DONE);
    }

    @Test
    void replayPayloadsMatchToolCallFormatterFormat() {
        Session session = Session.create();
        session.addMessage(Message.user("u"));
        session.addMessage(Message.assistant("", List.of(
                new ToolCall("c1", "read_file", "{\"path\":\"a.txt\"}"),
                new ToolCall("c2", "bash", "ls"))));
        session.addMessage(Message.tool("c1", "out1"));
        session.addMessage(Message.tool("c2", "out2"));
        session.addMessage(Message.assistant("done", List.of()));

        RecordingPort port = new RecordingPort();
        SessionReplayer.replay(session, port);

        assertThat(port.events).extracting(OutputEvent::type)
                .containsExactly(OutputEventType.USER, OutputEventType.TOOL_CALL,
                        OutputEventType.TOOL_RESULT, OutputEventType.TOOL_RESULT,
                        OutputEventType.TOKEN, OutputEventType.DONE);
        OutputEvent toolCall = port.events.get(1);
        assertThat(toolCall.payload())
                .isEqualTo("read_file({\"path\":\"a.txt\"}); bash(ls)");
    }

    @Test
    void replayBindsSessionIdAndSequencesFromZero() {
        Session session = Session.create();
        session.addMessage(Message.user("u"));
        session.addMessage(Message.assistant("a", List.of()));

        RecordingPort port = new RecordingPort();
        SessionReplayer.replay(session, port);

        assertThat(port.events).allSatisfy(e -> assertThat(e.sessionId()).isEqualTo(session.id()));
        assertThat(port.events).extracting(OutputEvent::seq)
                .containsExactly(0L, 1L, 2L);
    }

    @Test
    void replayEmptySessionEmitsOnlyDone() {
        RecordingPort port = new RecordingPort();
        SessionReplayer.replay(Session.create(), port);

        assertThat(port.types()).containsExactly(OutputEventType.DONE);
    }

    @Test
    void replaySkipsBlankAssistantContentButKeepsToolCall() {
        Session session = Session.create();
        session.addMessage(Message.assistant("", List.of(new ToolCall("c1", "goto", "home"))));

        RecordingPort port = new RecordingPort();
        SessionReplayer.replay(session, port);

        // 空正文的助手消息仅发工具调用事件，不再发 TOKEN
        assertThat(port.types()).containsExactly(OutputEventType.TOOL_CALL, OutputEventType.DONE);
    }
}
