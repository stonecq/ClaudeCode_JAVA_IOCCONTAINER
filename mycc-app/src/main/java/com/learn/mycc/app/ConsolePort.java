package com.learn.mycc.app;

import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;

import java.io.PrintStream;

/** 控制台渲染：把 OutputEvent 打印到流（M6 会用 JLine/ANSI 的 CliPort 替代）。 */
public final class ConsolePort implements InteractionPort {

    private final PrintStream out;
    /** 是否显示思考内容；默认隐藏，仅影响 THINKING 事件的渲染。 */
    private final boolean showReasoning;
    /** 是否正处在思考块中：首个 THINKING 前打 [思考] 前缀，下一个非思考事件换行收块。 */
    private boolean inThinking;

    public ConsolePort(PrintStream out) {
        this(out, false);
    }

    public ConsolePort(PrintStream out, boolean showReasoning) {
        this.out = out;
        this.showReasoning = showReasoning;
    }

    @Override
    public void onEvent(OutputEvent event) {
        switch (event.type()) {
            case THINKING -> {
                if (!showReasoning) {
                    return;
                }
                if (!inThinking) {
                    out.print("[思考] ");
                    inThinking = true;
                }
                out.print(event.payload());
            }
            case TOKEN -> {
                closeThinking();
                out.print(event.payload());
            }
            case TOOL_CALL -> {
                closeThinking();
                out.println("[工具] " + event.payload());
            }
            case TOOL_RESULT -> {
                closeThinking();
                out.println("[结果] " + event.payload());
            }
            case ERROR -> {
                closeThinking();
                out.println("[错误] " + event.payload());
            }
            case DONE -> {
                closeThinking();
                out.println();
            }
        }
    }

    private void closeThinking() {
        if (inThinking) {
            out.println();
            inThinking = false;
        }
    }
}