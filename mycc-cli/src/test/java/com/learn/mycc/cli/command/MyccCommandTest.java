package com.learn.mycc.cli.command;

import com.learn.mycc.agent.config.AgentConfig;
import com.learn.mycc.compact.Compactor;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.cli.CliContext;
import com.learn.mycc.cli.CliPort;
import com.learn.mycc.cli.ReplLoop;
import com.learn.mycc.cli.config.CliConfig;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.core.tool.ToolDefinition;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.storage.config.StorageConfig;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MyccCommandTest {

    @TempDir
    Path tempDir;

    private final StringWriter buffer = new StringWriter();
    private final PrintWriter out = new PrintWriter(buffer);
    private final String nl = System.lineSeparator();

    @BeforeEach
    void clearSystemProperty() {
        System.clearProperty("mycc.showReasoning");
    }

    @AfterEach
    void cleanSystemProperty() {
        System.clearProperty("mycc.showReasoning");
    }

    private SessionStore newStore() {
        return new SessionStore(new FileStorage(tempDir));
    }

    private ConfigService emptyConfig() {
        return new ConfigService(tempDir.resolve("config"));
    }

    private CliContext newContext(String input, SessionStore store, ConfigService config) {
        MockProvider provider = new MockProvider(Map.of("你好", "好的"));
        CliPort port = new CliPort(out, false, true);
        // 直注输入：测试不用 JLine 终端（真终端会抢 System.in，污染 surefire 管道），
        // 用 BufferedReader + StringReader 把脚本当输入喂给 ReplLoop
        BufferedReader reader = new BufferedReader(new StringReader(input));
        ReplLoop.LineInput lineInput = () -> reader.readLine();

        // 与生产同装配路径：建真实容器 + overrideSingleton 注入测试替身（临时存储/配置、
        // StringWriter 输出、StringReader 输入、Mock provider），再经 CliContext @Component
        // 字段注入取出——enterRepl 走的 getBean(AgentLoop/ReplLoop, args) 与生产完全一致
        IocContainer container = IocContainer.create();
        container.overrideSingleton(ConfigService.class, config);
        container.overrideSingleton(SessionStore.class, store);
        container.overrideSingleton(CliPort.class, port);
        container.overrideSingleton(ReplLoop.LineInput.class, lineInput);
        container.overrideSingleton(LlmProvider.class, provider);
        container.overrideSingleton(ApplicationConfig.class, new ApplicationConfig(tempDir));
        container.register(CliContext.class, ReplLoop.class, HookDispatcher.class, AgentConfig.class, CliConfig.class, Compactor.class, StorageConfig.class);
        return container.getBean(CliContext.class);
    }

    private int exec(CliContext ctx, String... args) {
        return new CommandLine(new MyccCommand(ctx))
                .addSubcommand("resume", new ResumeCommand(ctx))
                .addSubcommand("sessions", new SessionsCommand(ctx))
                .addSubcommand("tools", new ToolsCommand(ctx))
                .addSubcommand("config", new ConfigCommand(ctx))
                .execute(args);
    }

    @Test
    void bareCommandCreatesNewSessionAndEntersRepl() {
        SessionStore store = newStore();
        int code = exec(newContext("你好\n/exit\n", store, emptyConfig()));

        assertThat(code).isEqualTo(0);
        assertThat(buffer.toString()).contains("我 > 你好");
        assertThat(store.list()).hasSize(1);
    }

    @Test
    void resumeWithoutIdContinuesLatestAndReplaysHistory() {
        SessionStore store = newStore();
        Session s = Session.create();
        s.addMessage(Message.user("历史问题"));
        store.save(s);

        int code = exec(newContext("/exit\n", store, emptyConfig()), "resume");

        assertThat(code).isEqualTo(0);
        assertThat(buffer.toString()).contains("我 > 历史问题");
    }

    @Test
    void resumeWithIdContinuesSpecifiedSession() {
        SessionStore store = newStore();
        Session s = new Session("fixed-id");
        s.addMessage(Message.user("指定会话问题"));
        store.save(s);

        int code = exec(newContext("/exit\n", store, emptyConfig()), "resume", "fixed-id");

        assertThat(code).isEqualTo(0);
        assertThat(buffer.toString()).contains("我 > 指定会话问题");
    }

    @Test
    void resumeWithUnknownIdExitsOne() {
        int code = exec(newContext("/exit\n", newStore(), emptyConfig()), "resume", "no-such");

        assertThat(code).isEqualTo(1);
        assertThat(buffer.toString()).contains("找不到会话 no-such");
    }

    @Test
    void resumeWithoutHistoryExitsOne() {
        int code = exec(newContext("/exit\n", newStore(), emptyConfig()), "resume");

        assertThat(code).isEqualTo(1);
        assertThat(buffer.toString()).contains("没有历史会话");
    }

    @Test
    void sessionsListsIdTimeAndTitleNewestFirst() throws Exception {
        SessionStore store = newStore();
        Session first = new Session("alpha");
        first.addMessage(Message.user("第一段"));
        store.save(first);
        Session second = new Session("beta");
        second.addMessage(Message.user("第二段"));
        store.save(second);
        Files.setLastModifiedTime(tempDir.resolve("session/alpha.json"), FileTime.fromMillis(5000));
        Files.setLastModifiedTime(tempDir.resolve("session/beta.json"), FileTime.fromMillis(9000));

        int code = exec(newContext("/exit\n", store, emptyConfig()), "sessions");

        assertThat(code).isEqualTo(0);
        String rendered = buffer.toString();
        assertThat(rendered).contains("beta\t" + Instant.ofEpochMilli(9000) + "\t第二段");
        assertThat(rendered).contains("alpha\t" + Instant.ofEpochMilli(5000) + "\t第一段");
        assertThat(rendered.indexOf("beta")).isLessThan(rendered.indexOf("alpha"));
    }

    @Test
    void toolsListsRegisteredNameAndDescription() throws Exception {
        CliContext ctx = newContext("/exit\n", newStore(), emptyConfig());
        ctx.registry().register(new ToolDefinition("fake_tool", "示例工具", "", String.class.getMethod("length")));
        ctx.registry().register(new ToolDefinition("helper", "辅助工具", "", String.class.getMethod("length")));

        int code = exec(ctx, "tools");

        assertThat(code).isEqualTo(0);
        assertThat(buffer.toString())
                .contains("fake_tool\t示例工具")
                .contains("helper\t辅助工具");
    }

    @Test
    void configPrintsEffectiveValueFromFile() throws Exception {
        Path cfg = tempDir.resolve("config.json");
        Files.writeString(cfg, "{\"cli\":{\"showReasoning\":false}}", StandardCharsets.UTF_8);

        int code = exec(newContext("/exit\n", newStore(), new ConfigService(cfg)), "config");

        assertThat(code).isEqualTo(0);
        assertThat(buffer.toString()).isEqualTo("cli.showReasoning = false" + nl);
    }

    @Test
    void configPrintsDefaultWhenUnset() {
        int code = exec(newContext("/exit\n", newStore(), emptyConfig()), "config");

        assertThat(code).isEqualTo(0);
        assertThat(buffer.toString()).isEqualTo("cli.showReasoning = true" + nl);
    }
}