package com.learn.mycc.cli;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.annotation.Scope;
import com.learn.mycc.core.annotation.ScopeType;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import org.jline.reader.LineReader;

import java.io.IOException;

/**
 * 交互主循环：读行 → 以 USER 事件回显 → 驱动 agent 一轮；逐轮异常容忍，不崩会话。
 * <p>
 * 原型 {@link Component}：每轮交互由容器 {@code getBean(ReplLoop.class, port, agent::run,
 * input, sessionId)} 全参覆盖创建（port/runner/input/sessionId 为调用侧绑定值，不入容器）。
 * 通过两个函数式缝解耦：{@link LineInput} 供生产接 JLine LineReader、测试接 BufferedReader；
 * {@link AgentRunner} 供生产接 {@code agent::run}、测试注入抛异常 lambda。
 * 斜杠命令：/exit 结束；/clear 清屏（仅 ANSI 可用时有效）。EOF（Ctrl+D）结束循环。</p>
 */
@Component
@Scope(ScopeType.PROTOTYPE)
public final class ReplLoop {

    /** 一行输入来源；返回 null 表示 EOF（Ctrl+D）。 */
    @FunctionalInterface
    public interface LineInput {
        String readLine() throws IOException;
    }

    /** 驱动一轮对话；异常由循环捕获并以 ERROR 事件显示后继续下一轮。 */
    @FunctionalInterface
    public interface AgentRunner {
        void run(String userMessage);
    }

    private final CliPort port;
    private final AgentRunner runner;
    private final LineInput input;
    private final String sessionId;

    @Inject
    public ReplLoop(CliPort port, AgentRunner runner, LineInput input, String sessionId) {
        this.port = port;
        this.runner = runner;
        this.input = input;
        this.sessionId = sessionId;
    }

    /** 用 JLine LineReader 造输入源：EOF / 中断（Ctrl+C）统一视为结束（返回 null）。 */
    public static LineInput fromLineReader(LineReader reader) {
        return () -> {
            try {
                return reader.readLine();
            } catch (Exception e) {
                return null;
            }
        };
    }

    /** 运行完整交互循环，直至 /exit 或 EOF。 */
    public void run() {
        while (true) {
            String line;
            try {
                line = input.readLine();
            } catch (IOException e) {
                port.onEvent(new OutputEvent(OutputEventType.ERROR, "读取输入失败: " + e.getMessage(), sessionId, 0));
                continue;
            }
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("/exit")) {
                break;
            }
            if (line.equals("/clear")) {
                port.clear();
                continue;
            }
            port.onEvent(new OutputEvent(OutputEventType.USER, line, sessionId, 0));
            try {
                runner.run(line);
            } catch (Exception e) {
                port.onEvent(new OutputEvent(OutputEventType.ERROR, "本轮出错: " + e.getMessage(), sessionId, 0));
            }
        }
    }
}