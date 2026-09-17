package com.learn.mycc.cli;

import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;
import org.jline.reader.LineReader;

import java.io.IOException;

/**
 * 交互主循环：读行 → 普通输入驱动 agent 一轮、斜杠命令交给 {@link Host}；逐轮异常容忍，不崩会话。
 * <p>不依赖容器：由 {@link CliAdapter} 直接创建。内置命令 {@code /exit}（退出）、{@code /clear}（清屏）；
 * 其余 {@code /xxx}（如 /sessions、/resume、/tools、/config）交由 {@link Host#handleSlashCommand}。</p>
 * <p>解耦缝：{@link LineInput}（生产接 JLine LineReader、测试接 BufferedReader）；{@link Host}
 * （生产接 CliSessionHost、测试注入 fake）。</p>
 */
public final class ReplLoop {

    /** 一行输入来源；返回 null 表示 EOF（Ctrl+D）。 */
    @FunctionalInterface
    public interface LineInput {
        String readLine() throws IOException;
    }

    /** 会话宿主：提供当前会话 id、驱动一轮对话、处理斜杠命令。 */
    public interface Host {
        /** 当前会话 id（用于事件归属）。 */
        String sessionId();

        /** 驱动当前会话一轮：用户消息交给 agent，输出经端口下发。 */
        void runTurn(String userMessage);

        /** 处理一条斜杠命令（如 /sessions、/resume x）；未知命令由实现给出提示。 */
        void handleSlashCommand(String line);
    }

    private final CliPort port;
    private final Host host;
    private final LineInput input;

    public ReplLoop(CliPort port, Host host, LineInput input) {
        this.port = port;
        this.host = host;
        this.input = input;
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
                emitError("读取输入失败: " + e.getMessage());
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
            if (line.startsWith("/")) {
                host.handleSlashCommand(line);
                continue;
            }
            // USER 事件由 agent 在回合开始下发（见 AgentLoop.run），此处不再重复
            try {
                host.runTurn(line);
            } catch (Exception e) {
                emitError("本轮出错: " + e.getMessage());
            }
        }
    }

    private void emitError(String message) {
        port.onEvent(new OutputEvent(OutputEventType.ERROR, message, host.sessionId(), 0));
    }
}