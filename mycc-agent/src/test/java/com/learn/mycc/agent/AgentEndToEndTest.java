package com.learn.mycc.agent;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.tools.FileTools;
import com.learn.mycc.ui.OutputEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** M4 验收：一次 "写文件 → 读文件 → 最终回复" 多轮工具循环端到端。 */
class AgentEndToEndTest {

    @TempDir
    Path workspace;

    @Test
    void writeThenReadMultiTurn() throws IOException {
        ToolRegistry registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new FileTools(new ApplicationConfig(workspace)), "fileTools");

        MockProvider provider = MockProvider.scripted(request -> {
            long toolMessages = request.messages().stream()
                    .filter(m -> m.role() == ChatMessage.Role.TOOL).count();
            if (toolMessages == 0) {
                return new ChatResponse("", List.of(new ToolCall("call_1", "write_file",
                        "{\"path\":\"notes.txt\",\"content\":\"hello agent\"}")));
            }
            if (toolMessages == 1) {
                return new ChatResponse("", List.of(new ToolCall("call_2", "read_file",
                        "{\"path\":\"notes.txt\"}")));
            }
            return ChatResponse.text("已写入并读回：" + lastToolContent(request));
        });
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, registry, "mock", 10);

        String result = agent.run("把 hello agent 写入 notes.txt，再读出来");

        assertThat(result).isEqualTo("已写入并读回：hello agent");
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("hello agent");
        assertThat(port.types()).containsExactly(
                OutputEventType.USER,
                OutputEventType.TOOL_CALL,
                OutputEventType.TOOL_RESULT,
                OutputEventType.TOOL_CALL,
                OutputEventType.TOOL_RESULT,
                OutputEventType.TOKEN,
                OutputEventType.DONE);

        List<Message> history = agent.session().conversation().messages();
        assertThat(history).hasSize(6);
        assertThat(history).extracting(Message::role).containsExactly(
                ChatMessage.Role.USER,
                ChatMessage.Role.ASSISTANT,
                ChatMessage.Role.TOOL,
                ChatMessage.Role.ASSISTANT,
                ChatMessage.Role.TOOL,
                ChatMessage.Role.ASSISTANT);
        assertThat(history.get(2).content()).isEqualTo("已写入: notes.txt");
        assertThat(history.get(4).content()).isEqualTo("hello agent");
    }

    private static String lastToolContent(ChatRequest request) {
        return request.messages().stream()
                .filter(m -> m.role() == ChatMessage.Role.TOOL)
                .map(ChatMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
    }
}
