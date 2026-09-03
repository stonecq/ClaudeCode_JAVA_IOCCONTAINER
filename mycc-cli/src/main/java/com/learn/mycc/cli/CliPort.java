package com.learn.mycc.cli;

import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;

/**
 * CLI 渲染端口：把 {@link OutputEvent} 按类型分色输出到 JLine 提供的 {@link PrintWriter}。
 * <p>
 * ANSI 由构造注入（测试可覆写）+ 运行时 {@link #supportsAnsi} 判定：非 TTY（dumb）降级为
 * 无转义码的纯文本。免 prompt 延续 M5 决策：用户输入以 USER 事件「我 > 」前缀回显。
 */
public final class CliPort implements InteractionPort {

    private static final String RESET = "0";
    /** USER 前缀青色。 */
    private static final String CYAN = "36";
    /** THINKING 灰色 + 斜体。 */
    private static final String THINK_STYLE = "90;3";
    /** TOOL_CALL 黄色。 */
    private static final String YELLOW = "33";
    /** TOOL_RESULT 绿色。 */
    private static final String GREEN = "32";
    /** ERROR 红色。 */
    private static final String RED = "31";
    /** 清屏序列：先整屏清除再光标回原点。 */
    private static final String CLEAR_SCREEN = "\033[2J\033[H";

    /** 渲染输出目标；生产由 {@code terminal.writer()} 提供（JLine 统一处理 UTF-8 码页）。 */
    private final PrintWriter out;
    /** 是否启用 ANSI 转义；非 TTY / 测试关闭时走纯文本。 */
    private final boolean ansi;
    /** 是否显示思考内容；默认隐藏，仅影响 THINKING 事件。 */
    private final boolean showReasoning;
    /** 是否正处在思考块中：首个 THINKING 前打 [思考] 前缀，下一个非思考事件换行收块。 */
    private boolean inThinking;

    public CliPort(PrintWriter out, boolean ansi, boolean showReasoning) {
        this.out = out;
        this.ansi = ansi;
        this.showReasoning = showReasoning;
    }

    /** 依据终端类型判定是否应启用 ANSI：dumb（非 TTY/重定向/IDE）一律不启用。 */
    public static boolean supportsAnsi(Terminal terminal) {
        return !"dumb".equalsIgnoreCase(terminal.getType());
    }

    /** 共享输出目标：命令提示文本与事件渲染共用同一 sink，保证顺序一致。 */
    public PrintWriter writer() {
        return out;
    }

    /** 清屏；仅 ANSI 可用时真正清屏，否则为无副作用的 no-op。 */
    public void clear() {
        if (ansi) {
            out.print(CLEAR_SCREEN);
            out.flush();
        }
    }

    @Override
    public void onEvent(OutputEvent event) {
        String payload = event.payload() == null ? "" : event.payload();
        switch (event.type()) {
            case USER -> {
                closeThinking();
                out.println(style("我 > " + payload, CYAN));
            }
            case THINKING -> {
                if (!showReasoning) {
                    return;
                }
                if (!inThinking) {
                    out.print(style("[思考] ", THINK_STYLE));
                    inThinking = true;
                }
                out.print(style(payload, THINK_STYLE));
            }
            case TOKEN -> {
                closeThinking();
                out.print(style(payload, null));
            }
            case TOOL_CALL -> {
                closeThinking();
                out.println(style("[工具] " + payload, YELLOW));
            }
            case TOOL_RESULT -> {
                closeThinking();
                out.println(style("[结果] " + payload, GREEN));
            }
            case ERROR -> {
                closeThinking();
                out.println(style("[错误] " + payload, RED));
            }
            case DONE -> {
                closeThinking();
                out.println();
            }
        }
        out.flush();
    }

    /** 若正处于思考块中则换行收块，保证思考内容不与后续正文/前缀粘连。 */
    private void closeThinking() {
        if (inThinking) {
            out.println();
            inThinking = false;
        }
    }

    /** 按 ANSI 开关包裹转义码；code 为 null 或未启用 ANSI 时原样返回。 */
    private String style(String text, String code) {
        if (!ansi || code == null) {
            return text;
        }
        return "\033[" + code + "m" + text + "\033[" + RESET + "m";
    }
}