package com.learn.mycc.app;

import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ModelConfig;
import com.learn.mycc.ai.provider.OpenAiCompatProvider;
import com.learn.mycc.cli.CliContext;
import com.learn.mycc.cli.CliPort;
import com.learn.mycc.cli.command.ConfigCommand;
import com.learn.mycc.cli.command.MyccCommand;
import com.learn.mycc.cli.command.ResumeCommand;
import com.learn.mycc.cli.command.SessionsCommand;
import com.learn.mycc.cli.command.ToolsCommand;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.storage.file.FileStorage;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * v1 启动器（M6）：只装配并绑定 mycc-cli，然后执行 picocli 命令。
 * <p>
 * 流程：装配 IoC 容器 → 取 ToolRegistry → 按 OPENCODE_KEY 构建 provider（仅进入对话的
 * mycc/resume 缺 key 时引导退出非 0，只读命令免 key）→ 建 SessionStore / 终端 / CliPort /
 * REPL 输入 → 组装 CliContext → 注册子命令执行。
 * shade 打包后 {@code java -jar mycc-app/target/mycc-app.jar} 即可运行。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        // dumb(true)：非 TTY（管道/重定向/IDE）回落为 dumb 终端而不抛异常，保证降级可用
        try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(true).build()) {
            PrintWriter out = terminal.writer();
            MyccApplication application = new MyccApplication("com.learn.mycc");
            IocContainer container = application.getIocContainer();
            try {
                OpenAiCompatProvider provider = openCodeDsProvider(out);
                // 只读命令（--help/sessions/tools/config）不构造 AgentLoop、不调用 LLM，无 key 仍可用；
                // 仅进入对话的裸 mycc / resume 需要 LLM，缺 key 时输出引导提示后退出非 0
                boolean needsLlm = args.length == 0 || "resume".equals(args[0]);
                if (needsLlm && provider == null) {
                    out.flush();
                    System.exit(1);
                    return;
                }
                SessionStore store = new SessionStore(FileStorage.defaultDirectory());
                boolean showReasoning = Boolean.parseBoolean(new ConfigService().get("showReasoning", "true"));
                // 非 TTY 降级：dumb 终端 → 关闭 ANSI，纯文本输出
                boolean ansi = CliPort.supportsAnsi(terminal);
                CliPort port = new CliPort(terminal.writer(), ansi, showReasoning);
                LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
                // 钩子派发器：装配容器扫描到的 @Hook（含 WorkspacePaths 工作区路径校验），agent 循环据此拦截
                HookDispatcher dispatcher = new HookDispatcher(container.getHookRegistry());
                CliContext ctx = new CliContext(store, container.getToolRegistry(), provider,
                        new ConfigService(), "deepseek-v4-flash", 10, port, reader, dispatcher);
                int code = new CommandLine(new MyccCommand(ctx))
                        .addSubcommand("resume", new ResumeCommand(ctx))
                        .addSubcommand("sessions", new SessionsCommand(ctx))
                        .addSubcommand("tools", new ToolsCommand(ctx))
                        .addSubcommand("config", new ConfigCommand(ctx))
                        .execute(args);
                // JLine terminal.writer() 为缓冲 PrintWriter，System.exit 不触发 close/autoflush，
                // 需在退出前显式 flush，否则 sessions/tools/config 等只 println 的命令输出会丢失
                out.flush();
                System.exit(code);
            } finally {
                container.close();
            }
        }
    }

    /**
     * 依据环境变量 OPENCODE_KEY 构造 openCode 兼容网关的 provider。
     * key 缺失或空白时输出引导提示并返回 null；key 只存在进程环境，不写入代码与日志。
     */
    private static OpenAiCompatProvider openCodeDsProvider(PrintWriter out) {
        String apiKey = System.getenv("OPENCODE_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            out.println("未设置环境变量 OPENCODE_KEY，无法接入 openCode。");
            out.println("示例：OPENCODE_KEY=sk-xxx java -jar mycc-app/target/mycc-app.jar");
            return null;
        }
        return new OpenAiCompatProvider(new ModelConfig(apiKey, "https://opencode.ai/zen/go/v1"));
    }
}