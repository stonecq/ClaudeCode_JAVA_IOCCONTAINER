package com.learn.mycc.cli;

import com.learn.mycc.cli.repl.CliPermissionPrompt;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.storage.config.ConfigDefaults;
import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.UiAdapter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.DumbTerminal;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * CLI UI 适配器：启动即进入 REPL 对话界面（{@code --ui cli}）。
 * <p>自建 CLI 传输物（JLine 终端 / 读入 / {@link CliPort} / 审批提示），只经 {@link AgentApi}
 * 与 agent 交互；原 {@code resume/sessions/tools/config} 子命令已改为 REPL 内斜杠命令。</p>
 */
public final class CliAdapter implements UiAdapter {

    private CliPort port;
    private CliPermissionPrompt confirm;
    private ReplLoop.LineInput input;

    @Override
    public String id() {
        return "cli";
    }

    @Override
    public InteractionPort port(AgentApi agent) {
        ensureTerminalIo(agent);
        return port;
    }

    @Override
    public UserConfirmation userConfirmation(AgentApi agent) {
        ensureTerminalIo(agent);
        return confirm;
    }

    @Override
    public void start(AgentApi agent, String[] args) {
        ensureTerminalIo(agent);
        if (missingKey()) {
            PrintWriter out = port.writer();
            out.println("未设置环境变量 OPENCODE_KEY，无法接入 openCode。");
            out.println("示例：OPENCODE_KEY=sk-xxx java -jar mycc-app/target/mycc-app.jar --ui cli");
            out.flush();
            System.exit(1);
        }
        // 默认新建会话进 REPL；切换会话用 /sessions 查看、/resume <id> 进入
        String sessionId = agent.createSession();
        CliSessionHost host = new CliSessionHost(agent, port, sessionId);
        new ReplLoop(port, host, input).run();
        port.writer().flush();
    }

    /** 惰性构建 CLI 传输物（终端 / 读入 / 渲染端口 / 审批提示）并缓存复用。 */
    private void ensureTerminalIo(AgentApi agent) {
        if (port != null) {
            return;
        }
        Terminal terminal = terminal();
        PrintWriter out = terminal.writer();
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
        input = ReplLoop.fromLineReader(reader);
        boolean ansi = CliPort.supportsAnsi(terminal);
        boolean showReasoning = "true".equalsIgnoreCase(agent.config(ConfigDefaults.CLI_SHOW_REASONING));
        port = new CliPort(out, ansi, showReasoning);
        confirm = new CliPermissionPrompt(input, out);
    }

    /** 无控制台（管道/重定向/CI）直接建 {@link DumbTerminal}，避免 JLine 探测原生终端拖慢启动。 */
    private static Terminal terminal() {
        try {
            if (System.console() == null) {
                return new DumbTerminal(System.in, System.out);
            }
            return TerminalBuilder.builder().system(true).dumb(true).build();
        } catch (IOException e) {
            throw new MyccException("初始化终端失败: " + e.getMessage(), e);
        }
    }

    /** 环境变量 OPENCODE_KEY 缺失或空白即视为不可用；key 只存在于进程环境，不进代码与日志。 */
    private static boolean missingKey() {
        String apiKey = System.getenv("OPENCODE_KEY");
        return apiKey == null || apiKey.isBlank();
    }
}