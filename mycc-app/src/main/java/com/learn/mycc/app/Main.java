package com.learn.mycc.app;

import com.learn.mycc.cli.CliPort;
import com.learn.mycc.cli.command.ConfigCommand;
import com.learn.mycc.cli.command.MyccCommand;
import com.learn.mycc.cli.command.ResumeCommand;
import com.learn.mycc.cli.command.SessionsCommand;
import com.learn.mycc.cli.command.ToolsCommand;
import com.learn.mycc.core.context.IocContainer;
import picocli.CommandLine;

import java.io.PrintWriter;

/**
 * v1 启动器（M6，Spring 化重构 S2）：装配根只做「开容器 → start → 执行命令 → close」。
 * <p>
 * 全部实例（终端/Provider/存储/钩子/命令）都由容器纳管：构造 {@link MyccApplication} 即
 * create + register，显式 {@code start()} 预创建单例（prototype 按需），picocli 命令从容器
 * {@code getBean} 取得。仅聊天命令（裸 mycc / resume）缺 OPENCODE_KEY 时预检引导退出非 0，
 * 只读命令（--help/sessions/tools/config）免 key。
 * shade 打包后 {@code java -jar mycc-app/target/mycc-app.jar} 即可运行。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        MyccApplication application = new MyccApplication();
        IocContainer container = application.getIocContainer();
        try {
            application.start();
            // 只读命令不构造 AgentLoop、不调用 LLM，无 key 仍可用；仅进入对话的裸 mycc / resume
            // 需要 LLM，缺 key 时输出引导提示后退出非 0
            boolean needsLlm = args.length == 0 || "resume".equals(args[0]);
            if (needsLlm && missingKey()) {
                PrintWriter out = container.getBean(CliPort.class).writer();
                out.println("未设置环境变量 OPENCODE_KEY，无法接入 openCode。");
                out.println("示例：OPENCODE_KEY=sk-xxx java -jar mycc-app/target/mycc-app.jar");
                out.flush();
                container.close();
                System.exit(1);
                return;
            }
            int code = new CommandLine(container.getBean(MyccCommand.class))
                    .addSubcommand("resume", container.getBean(ResumeCommand.class))
                    .addSubcommand("sessions", container.getBean(SessionsCommand.class))
                    .addSubcommand("tools", container.getBean(ToolsCommand.class))
                    .addSubcommand("config", container.getBean(ConfigCommand.class))
                    .execute(args);
            // JLine PrintWriter 为缓冲输出，System.exit 不触发 close/autoflush，须先显式 flush
            PrintWriter out = container.getBean(CliPort.class).writer();
            out.flush();
            container.close();
            System.exit(code);
        } finally {
            // 异常路径（start/execute 失败）兜底销毁；normal 路径已 close，close 幂等
            container.close();
        }
    }

    /** 环境变量 OPENCODE_KEY 缺失或空白即视为不可用；key 只存在于进程环境，不进代码与日志。 */
    private static boolean missingKey() {
        String apiKey = System.getenv("OPENCODE_KEY");
        return apiKey == null || apiKey.isBlank();
    }
}