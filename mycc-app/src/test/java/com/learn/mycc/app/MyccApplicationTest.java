package com.learn.mycc.app;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.cli.CliPort;
import com.learn.mycc.cli.ReplLoop;
import com.learn.mycc.cli.repl.CliPermissionPrompt;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyccApplicationTest {

    @Test
    void assemblesAllTenBuiltinTools() {
        MyccApplication application = new MyccApplication();
        application.start();
        IocContainer container = application.getIocContainer();
        try {
            ToolRegistry registry = container.getToolRegistry();
            List<String> names = registry.getAll().stream().map(tool -> tool.getName()).toList();
            assertThat(names).containsExactlyInAnyOrder(
                    "read_file", "write_file", "edit_file", "bash", "glob", "grep", "search_files",
                    "read_memory", "save_memory", "delete_memory");
        } finally {
            container.close();
        }
    }

    @Test
    void cliUserConfirmationShadowsUnavailable() {
        // 容器装载后 UserConfirmation 应为 CLI 专属实现（精确命中），而非无 UI 兜底的 Unavailable
        MyccApplication application = new MyccApplication();
        application.start();
        IocContainer container = application.getIocContainer();
        try {
            assertThat(container.getBean(UserConfirmation.class)).isInstanceOf(CliPermissionPrompt.class);
        } finally {
            container.close();
        }
    }

    @Test
    void enterReplContainerPathWiresPrototypes() {
        // 生产 enterRepl 走容器装配原型：args 覆盖绑定会话 / 全参覆盖 ReplLoop——把这条
        // 真实 wiring 纳入回归，堵住「单测只碰测试缝、生产装配无覆盖」的盲区
        MyccApplication application = new MyccApplication();
        application.start();
        IocContainer container = application.getIocContainer();
        try {
            Session session = Session.create();
            AgentLoop agent = container.getBean(AgentLoop.class, session);
            assertThat(agent.session()).isSameAs(session);

            // ReplLoop 全参覆盖：用自己的端口/输入驱动一行，验证 runner 与输出均被捕获
            StringWriter buffer = new StringWriter();
            CliPort port = new CliPort(new PrintWriter(buffer), false, true);
            List<String> seen = new ArrayList<>();
            ReplLoop.AgentRunner runner = seen::add;
            Iterator<String> feed = List.of("probe").iterator();
            ReplLoop.LineInput input = () -> feed.hasNext() ? feed.next() : null;
            ReplLoop repl = container.getBean(ReplLoop.class, port, runner, input, "sid-1");
            repl.run();
            assertThat(seen).containsExactly("probe");
            assertThat(buffer.toString()).contains("我 > probe");
        } finally {
            container.close();
        }
    }
}
