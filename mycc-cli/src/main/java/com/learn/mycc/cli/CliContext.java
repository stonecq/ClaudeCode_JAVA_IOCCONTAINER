package com.learn.mycc.cli;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.loop.SessionReplayer;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import org.jline.reader.LineReader;

import java.io.PrintWriter;

/**
 * 命令装配期上下文：承载命令执行所需的全部组件，并把「绑定会话 → 回放历史 → 建 AgentLoop
 * → 进 REPL」编排归一到 {@link #enterRepl}，命令只负责触发，不重复装配逻辑。
 * 本类为 {@code @Component}，依赖一律字段注入；AgentLoop / ReplLoop 由容器按需装配（prototype），
 * 容器引用经 {@code @Inject IocContainer} 注入——enterRepl 走 getBean(AgentLoop/ReplLoop, args)，
 * 不存在脱离容器的手工直装分支。
 */
@Component
public final class CliContext {

    /** 会话存储：新建/续聊的持久化载体。 */
    @Inject
    private SessionStore store;

    /** 工具注册表：agent 循环取已注册工具声明。 */
    @Inject
    private ToolRegistry registry;

    /** LLM 提供方：agent 循环的模型调用入口。 */
    @Inject
    private LlmProvider provider;

    /** 配置服务：读取 showReasoning 等生效值。 */
    @Inject
    private ConfigService config;

    /** CLI 渲染端口：命令提示文本与事件渲染共用同一 sink。 */
    @Inject
    private CliPort port;

    /** REPL 行输入来源：生产由 JLine {@link LineReader} 适配（沿用 EOF/中断归一化契约）。 */
    @Inject
    private ReplLoop.LineInput input;

    /** 钩子派发器：tool_call_before 等事件的分发入口。 */
    @Inject
    private HookDispatcher hooks;

    /** 容器引用：enterRepl 依赖它装配 AgentLoop/ReplLoop 原型。 */
    @Inject
    private IocContainer container;

    public CliContext() {
    }

    public SessionStore store() {
        return store;
    }

    public ToolRegistry registry() {
        return registry;
    }

    public LlmProvider provider() {
        return provider;
    }

    public ConfigService config() {
        return config;
    }

    public CliPort port() {
        return port;
    }

    /** 共享输出目标：命令的提示文本与事件渲染走同一 PrintWriter。 */
    public PrintWriter out() {
        return port.writer();
    }

    /**
     * 绑定会话进入 REPL：replayHistory 为 true 时先经 {@link SessionReplayer} 回放历史，
     * 再以显式会话绑 {@link AgentLoop}（每轮落盘）。装配唯一入口是容器：经
     * {@code getBean(AgentLoop.class, session)} 绑定会话、{@code getBean(ReplLoop.class,
     * port, agent::run, input, session.id())} 全参覆盖。
     *
     * @param session        会话（新建或已从存储加载），不可为 null
     * @param replayHistory  是否先把该会话历史回放为与实时一致的事件流
     * @return 待运行的 ReplLoop；由命令调用 {@code run()} 消费完整交互循环
     */
    public ReplLoop enterRepl(Session session, boolean replayHistory) {
        if (replayHistory) {
            SessionReplayer.replay(session, port);
        }
        container.overrideSingleton(Session.class, session);
        AgentLoop agent = container.getBean(AgentLoop.class, session);
        ReplLoop.AgentRunner runner = agent::run;
        return container.getBean(ReplLoop.class, port, runner, input, session.id());
    }
}