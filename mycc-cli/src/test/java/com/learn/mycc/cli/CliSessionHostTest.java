package com.learn.mycc.cli;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.hook.HookRegistry;
import com.learn.mycc.core.skill.SkillRegistry;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CliSessionHostTest {

    @TempDir
    Path tempDir;

    StringWriter buffer;
    CliPort port;
    CliSessionHost host;
    SessionStore store;

    /** /tools 用到的夹具工具。 */
    static final class FixtureTools {
        @Tool(name = "demo", description = "示例工具")
        public String demo() {
            return "";
        }
    }

    @BeforeEach
    void setUp() {
        buffer = new StringWriter();
        port = new CliPort(new PrintWriter(buffer), false, true);
        store = new SessionStore(new FileStorage(tempDir));
        ToolRegistry registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new FixtureTools(), "fixture");
        ConfigService config = new ConfigService(tempDir.resolve("config.json"));
        host = new CliSessionHost(store, registry, config, port, new StubContainer(port), Session.create());
    }

    @Test
    void sessionsListsSavedSessions() {
        Session saved = Session.create();
        saved.addMessage(Message.user("第一句"));
        store.save(saved);

        host.handleSlashCommand("/sessions");

        assertThat(buffer.toString()).contains(saved.id());
    }

    @Test
    void sessionsPrintsPlaceholderWhenEmpty() {
        host.handleSlashCommand("/sessions");
        assertThat(buffer.toString()).contains("（暂无历史会话）");
    }

    @Test
    void resumeSwitchesCurrentSession() {
        Session saved = Session.create();
        saved.addMessage(Message.user("历史问题"));
        store.save(saved);

        host.handleSlashCommand("/resume " + saved.id());

        assertThat(host.sessionId()).isEqualTo(saved.id());
        assertThat(buffer.toString()).contains("已切换到会话 " + saved.id());
    }

    @Test
    void resumeMissingIdReportsNotFound() {
        host.handleSlashCommand("/resume no-such");
        assertThat(buffer.toString()).contains("找不到会话 no-such");
    }

    @Test
    void resumeWithoutIdReportsNoHistory() {
        host.handleSlashCommand("/resume");
        assertThat(buffer.toString()).contains("没有历史会话");
    }

    @Test
    void toolsListsRegisteredTools() {
        host.handleSlashCommand("/tools");
        assertThat(buffer.toString()).contains("demo\t示例工具");
    }

    @Test
    void configPrintsEffectiveValue() {
        host.handleSlashCommand("/config");
        assertThat(buffer.toString()).contains("cli.showReasoning = true");
    }

    @Test
    void unknownCommandGivesHint() {
        host.handleSlashCommand("/nope");
        assertThat(buffer.toString()).contains("未知命令: /nope");
    }

    @Test
    void cachesAgentLoopPerSessionAndRebuildsOnResume() {
        StubContainer container = new StubContainer(port);
        CliSessionHost h = new CliSessionHost(store, new ToolRegistry(), new ConfigService(tempDir.resolve("c2.json")),
                port, container, Session.create());

        h.runTurn("a");
        h.runTurn("b");
        assertThat(container.agentBuilds).isEqualTo(1); // 同会话复用同一循环

        Session other = Session.create();
        store.save(other);
        h.handleSlashCommand("/resume " + other.id());
        h.runTurn("c");
        assertThat(container.agentBuilds).isEqualTo(2); // 切换会话后重建
    }

    /** 只实现 getBean(Class,args)（装配一个可跑的 AgentLoop 并计数）的容器替身。 */
    static final class StubContainer implements IocContainer {
        private final CliPort port;
        int agentBuilds;

        StubContainer(CliPort port) {
            this.port = port;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getBean(Class<T> type, Object... args) {
            agentBuilds++;
            Session session = (Session) args[0];
            AgentLoop loop = AgentLoop.withToolRegistry(port,
                    MockProvider.scripted(request -> ChatResponse.text("ok")),
                    new ToolRegistry(), "mock", 1, null, session);
            return (T) loop;
        }

        @Override
        public void register(BeanDefinition... definitions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void register(String basePackage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void register(Class<?>... types) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerSingleton(Class<?> type, Object instance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void overrideSingleton(Class<?> type, Object instance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addBeanPostProcessor(com.learn.mycc.core.bean.BeanPostProcessor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void start() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getBean(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getBean(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.List<T> getBeansOfType(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ToolRegistry getToolRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HookRegistry getHookRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SkillRegistry getSkillRegistry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }
    }
}