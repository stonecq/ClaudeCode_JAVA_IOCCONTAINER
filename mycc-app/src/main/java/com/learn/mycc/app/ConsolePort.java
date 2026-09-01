package com.learn.mycc.app;

import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;

import java.io.PrintStream;

/** 控制台渲染：把 OutputEvent 打印到流（M6 会用 JLine/ANSI 的 CliPort 替代）。 */
public final class ConsolePort implements InteractionPort {

    private final PrintStream out;

    public ConsolePort(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(OutputEvent event) {
        switch (event.type()) {
            case TOKEN -> out.print(event.payload());
            case TOOL_CALL -> out.println("[工具] " + event.payload());
            case TOOL_RESULT -> out.println("[结果] " + event.payload());
            case ERROR -> out.println("[错误] " + event.payload());
            case DONE -> out.println();
        }
    }
}
