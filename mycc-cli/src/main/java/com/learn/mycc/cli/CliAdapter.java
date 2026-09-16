package com.learn.mycc.cli;

import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.ui.UiAdapter;

import java.io.PrintWriter;

/**
 * CLI UI 适配器：启动即进入 REPL 对话界面。
 * <p>原 {@code resume/sessions/tools/config} 子命令已改为 REPL 内斜杠命令
 * （{@code /sessions}、{@code /resume}、{@code /tools}、{@code /config}），启动参数只剩 {@code --ui cli}。</p>
 */
public final class CliAdapter implements UiAdapter {

    @Override
    public String id() {
        return "cli";
    }

    @Override
    public void start(IocContainer container, String[] args) {
        CliPort port = container.getBean(CliPort.class);
        if (missingKey()) {
            PrintWriter out = port.writer();
            out.println("未设置环境变量 OPENCODE_KEY，无法接入 openCode。");
            out.println("示例：OPENCODE_KEY=sk-xxx java -jar mycc-app/target/mycc-app.jar --ui cli");
            out.flush();
            System.exit(1);
        }
        ReplLoop.LineInput input = container.getBean(ReplLoop.LineInput.class);
        // 默认新建会话进 REPL；切换会话用 /sessions 查看、/resume <id> 进入
        CliSessionHost host = new CliSessionHost(
                container.getBean(SessionStore.class),
                container.getBean(ToolRegistry.class),
                container.getBean(ConfigService.class),
                port, container, Session.create());
        new ReplLoop(port, host, input).run();
        port.writer().flush();
    }

    /** 环境变量 OPENCODE_KEY 缺失或空白即视为不可用；key 只存在于进程环境，不进代码与日志。 */
    private static boolean missingKey() {
        String apiKey = System.getenv("OPENCODE_KEY");
        return apiKey == null || apiKey.isBlank();
    }
}