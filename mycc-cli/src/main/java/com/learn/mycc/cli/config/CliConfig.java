package com.learn.mycc.cli.config;

import com.learn.mycc.cli.CliPort;
import com.learn.mycc.cli.ReplLoop;
import com.learn.mycc.cli.repl.CliPermissionPrompt;
import com.learn.mycc.core.annotation.Bean;
import com.learn.mycc.core.annotation.Configuration;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.storage.config.ConfigService;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.DumbTerminal;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * CLI 装配配置：终端/输出/输入/审批等运行时组件全部以 @Bean 交给容器纳管。
 * {@link Terminal} 以 destroyMethod=close 入容器，随容器 close 逆序销毁；
 * {@link UserConfirmation} 按接口返回类型精确命中，遮蔽无 UI 环境默认的
 * UnavailableUserConfirmation。ReplLoop 为独立 prototype @Component，会话 id 与 agent
 * 回调由调用侧经 {@code getBean(ReplLoop.class, port, agent::run, input, sessionId)} 全参覆盖。
 */
@Configuration
public class CliConfig {

    /**
     * 构建 JLine 终端。无控制台（管道/重定向/CI）时直接建 {@link DumbTerminal}——避免
     * JLine 逐个探测原生终端（Windows 上可达数秒，且残留原生线程），保证只读命令与测试
     * 秒启、JVM 干净退出；有交互控制台时构建系统终端（dumb(true) 兜底）。
     */
    @Bean(destroyMethod = "close")
    public Terminal terminal() throws IOException {
        if (System.console() == null) {
            return new DumbTerminal(System.in, System.out);
        }
        return TerminalBuilder.builder().system(true).dumb(true).build();
    }

    @Bean
    public PrintWriter printWriter(Terminal terminal) {
        return terminal.writer();
    }

    @Bean
    public LineReader lineReader(Terminal terminal) {
        return LineReaderBuilder.builder().terminal(terminal).build();
    }

    /** REPL 行输入适配：EOF / 中断（Ctrl+C）统一视为结束。 */
    @Bean
    public ReplLoop.LineInput lineInput(LineReader reader) {
        return ReplLoop.fromLineReader(reader);
    }

    @Bean
    public CliPort cliPort(Terminal terminal, PrintWriter out, ConfigService config) {
        boolean ansi = CliPort.supportsAnsi(terminal);
        boolean showReasoning = Boolean.parseBoolean(config.get("showReasoning", "true"));
        return new CliPort(out, ansi, showReasoning);
    }

    /** CLI 专属人工审批；按接口返回类型精确命中，遮蔽默认的 UnavailableUserConfirmation。 */
    @Bean
    public UserConfirmation userConfirmation(ReplLoop.LineInput input, PrintWriter out) {
        return new CliPermissionPrompt(input, out);
    }
}