package com.learn.mycc.cli;

import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CliPortTest {

    private final StringWriter buffer = new StringWriter();
    private final PrintWriter out = new PrintWriter(buffer);
    private final String nl = System.lineSeparator();

    private static OutputEvent event(OutputEventType type, String payload) {
        return new OutputEvent(type, payload, "s1", 0);
    }

    private CliPort port(boolean ansi, boolean showReasoning) {
        return new CliPort(out, ansi, showReasoning);
    }

    @Test
    void hidesThinkingByDefault() {
        port(false, false).onEvent(event(OutputEventType.THINKING, "不该显示的思考"));
        port(false, false).onEvent(event(OutputEventType.TOKEN, "正式回答"));
        port(false, false).onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString()).isEqualTo("正式回答" + nl);
    }

    @Test
    void showsThinkingAsDistinctBlockWhenEnabled() {
        CliPort p = port(false, true);
        p.onEvent(event(OutputEventType.THINKING, "让我想想 "));
        p.onEvent(event(OutputEventType.THINKING, "先分析"));
        p.onEvent(event(OutputEventType.TOKEN, "正式回答"));
        p.onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString())
                .isEqualTo("[思考] 让我想想 先分析" + nl + "正式回答" + nl);
    }

    @Test
    void rendersUserTurn() {
        port(false, false).onEvent(event(OutputEventType.USER, "读一下文件"));
        port(false, false).onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString()).isEqualTo("我 > 读一下文件" + nl + nl);
    }

    @Test
    void closesThinkingBlockBeforeUserEvent() {
        CliPort p = port(false, true);
        p.onEvent(event(OutputEventType.THINKING, "推理"));
        p.onEvent(event(OutputEventType.USER, "新问题"));
        p.onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString())
                .isEqualTo("[思考] 推理" + nl + "我 > 新问题" + nl + nl);
    }

    @Test
    void closesThinkingBlockBeforeToolEvent() {
        CliPort p = port(false, true);
        p.onEvent(event(OutputEventType.THINKING, "推理过程"));
        p.onEvent(event(OutputEventType.TOOL_CALL, "read_file(x)"));
        p.onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString())
                .isEqualTo("[思考] 推理过程" + nl + "[工具] read_file(x)" + nl + nl);
    }

    @Test
    void colorsEventsWithAnsiWhenEnabled() {
        CliPort p = port(true, true);
        p.onEvent(event(OutputEventType.USER, "你好"));
        p.onEvent(event(OutputEventType.THINKING, "想想"));
        p.onEvent(event(OutputEventType.TOOL_CALL, "read_file(a)"));
        p.onEvent(event(OutputEventType.TOOL_RESULT, "ok"));
        p.onEvent(event(OutputEventType.ERROR, "出错"));
        p.onEvent(event(OutputEventType.TOKEN, "正文"));
        p.onEvent(event(OutputEventType.DONE, ""));

        String s = buffer.toString();
        assertThat(s).contains("\033[36m我 > 你好\033[0m");
        assertThat(s).contains("\033[90;3m[思考] \033[0m\033[90;3m想想\033[0m");
        assertThat(s).contains("\033[33m[工具] read_file(a)\033[0m");
        assertThat(s).contains("\033[32m[结果] ok\033[0m");
        assertThat(s).contains("\033[31m[错误] 出错\033[0m");
        assertThat(s).contains("正文");
    }

    @Test
    void emitsNoAnsiEscapesWhenDisabled() {
        CliPort p = port(false, true);
        p.onEvent(event(OutputEventType.USER, "你好"));
        p.onEvent(event(OutputEventType.THINKING, "想想"));
        p.onEvent(event(OutputEventType.TOOL_CALL, "read_file(a)"));
        p.onEvent(event(OutputEventType.TOOL_RESULT, "ok"));
        p.onEvent(event(OutputEventType.ERROR, "出错"));
        p.onEvent(event(OutputEventType.DONE, ""));

        assertThat(buffer.toString()).doesNotContain("\033");
    }

    /** payload 可为 null（契约文档化）：渲染层应将 null 视为空串，不输出字面 "null"。 */
    @Test
    void rendersNullPayloadAsEmptyNotLiteralNull() {
        CliPort p = port(false, true);
        p.onEvent(new com.learn.mycc.ui.OutputEvent(OutputEventType.USER, null, "s1", 0));
        p.onEvent(new com.learn.mycc.ui.OutputEvent(OutputEventType.THINKING, null, "s1", 0));
        p.onEvent(new com.learn.mycc.ui.OutputEvent(OutputEventType.DONE, "", "s1", 0));

        assertThat(buffer.toString()).doesNotContain("null");
    }
}