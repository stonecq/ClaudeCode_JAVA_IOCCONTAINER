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
    void assemblesAllSixteenBuiltinTools() {
        MyccApplication application = new MyccApplication();
        application.start();
        IocContainer container = application.getIocContainer();
        try {
            ToolRegistry registry = container.getToolRegistry();
            List<String> names = registry.getAll().stream().map(tool -> tool.getName()).toList();
            assertThat(names).containsExactlyInAnyOrder(
                    "read_file", "write_file", "edit_file", "bash", "glob", "grep", "search_files",
                    "load_index", "read_memory", "save_memory", "delete_memory", "invoke_skill",
                    "create_plan", "complete_step", "read_plan", "subagent");
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
    void containerAssemblesAgentLoopBoundToSession() {
        // 生产路径：AgentLoop 走容器 prototype 装配、args 覆盖绑定会话——纳入回归
        MyccApplication application = new MyccApplication();
        application.start();
        IocContainer container = application.getIocContainer();
        try {
            Session session = Session.create();
            AgentLoop agent = container.getBean(AgentLoop.class, session);
            assertThat(agent.session()).isSameAs(session);
        } finally {
            container.close();
        }
    }
}
