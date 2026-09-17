package com.learn.mycc.app;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.permission.UnavailableUserConfirmation;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;
import org.junit.jupiter.api.Test;

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
    void bareContainerUsesUnavailableUserConfirmation() {
        // 容器只装 agent，不含 UI：无 UI 登记的审批端口为 fail-closed 的 Unavailable
        MyccApplication application = new MyccApplication();
        application.start();
        IocContainer container = application.getIocContainer();
        try {
            assertThat(container.getBean(UserConfirmation.class)).isInstanceOf(UnavailableUserConfirmation.class);
        } finally {
            container.close();
        }
    }

    @Test
    void containerAssemblesAgentLoopBoundToSessionWhenPortProvided() {
        // agent 的外向端口由 UI 提供并在 start 前登记；登记后 AgentLoop 可按会话装配
        MyccApplication application = new MyccApplication();
        IocContainer container = application.getIocContainer();
        try {
            container.registerSingleton(InteractionPort.class, new NoopPort());
            container.registerSingleton(UserConfirmation.class, new UnavailableUserConfirmation());
            application.start();

            Session session = Session.create();
            AgentLoop agent = container.getBean(AgentLoop.class, session);
            assertThat(agent.session()).isSameAs(session);
        } finally {
            container.close();
        }
    }

    /** 无副作用的交互端口替身。 */
    static final class NoopPort implements InteractionPort {
        @Override
        public void onEvent(OutputEvent event) {
        }
    }
}