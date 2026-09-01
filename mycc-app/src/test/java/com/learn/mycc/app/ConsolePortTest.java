package com.learn.mycc.app;

import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolePortTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    private final String nl = System.lineSeparator();

    private static OutputEvent event(OutputEventType type, String payload) {
        return new OutputEvent(type, payload, "s1", 0);
    }

    @Test
    void hidesThinkingByDefault() {
        ConsolePort port = new ConsolePort(out);

        port.onEvent(event(OutputEventType.THINKING, "不该显示的思考"));
        port.onEvent(event(OutputEventType.TOKEN, "正式回答"));
        port.onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString(StandardCharsets.UTF_8)).isEqualTo("正式回答" + nl);
    }

    @Test
    void showsThinkingAsDistinctBlockWhenEnabled() {
        ConsolePort port = new ConsolePort(out, true);

        port.onEvent(event(OutputEventType.THINKING, "让我想想 "));
        port.onEvent(event(OutputEventType.THINKING, "先分析"));
        port.onEvent(event(OutputEventType.TOKEN, "正式回答"));
        port.onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString(StandardCharsets.UTF_8))
                .isEqualTo("[思考] 让我想想 先分析" + nl + "正式回答" + nl);
    }

    @Test
    void closesThinkingBlockBeforeToolEvent() {
        ConsolePort port = new ConsolePort(out, true);

        port.onEvent(event(OutputEventType.THINKING, "推理过程"));
        port.onEvent(event(OutputEventType.TOOL_CALL, "read_file(x)"));
        port.onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString(StandardCharsets.UTF_8))
                .isEqualTo("[思考] 推理过程" + nl + "[工具] read_file(x)" + nl + nl);
    }
}