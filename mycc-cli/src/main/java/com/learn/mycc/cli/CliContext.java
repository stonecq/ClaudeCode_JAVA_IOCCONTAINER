package com.learn.mycc.cli;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.loop.SessionReplayer;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import org.jline.reader.LineReader;

import java.io.PrintWriter;

/**
 * 命令装配期上下文：承载命令执行所需的全部组件，并把「绑定会话 → 回放历史 → 建 AgentLoop
 * → 进 REPL」编排归一到 {@link #enterRepl}，命令只负责触发，不重复装配逻辑。
 */
public final class CliContext {

    /** 会话存储：新建/续聊的持久化载体。 */
    private final SessionStore store;
    /** 工具注册表：agent 循环取已注册工具声明。 */
    private final ToolRegistry registry;
    /** LLM 提供方：agent 循环的模型调用入口。 */
    private final LlmProvider provider;
    /** 配置服务：读取 showReasoning 等生效值。 */
    private final ConfigService config;
    /** LLM 模型名，透传给 {@link AgentLoop}。 */
    private final String model;
    /** 单轮最大迭代次数，透传给 {@link AgentLoop}。 */
    private final int maxIterations;
    /** CLI 渲染端口：命令提示文本与事件渲染共用同一 sink。 */
    private final CliPort port;
    /** REPL 行输入来源：生产由 JLine {@link LineReader} 适配（沿用 EOF/中断归一化契约），测试直达注。 */
    private final ReplLoop.LineInput input;
    /** 钩子派发器：tool_call_before 等事件的分发入口（可 null，表示不启用钩子）。 */
    private final HookDispatcher hooks;

    /** 生产装配：接 JLine {@link LineReader}，内部适配为 {@link ReplLoop.LineInput}，不装配钩子。 */
    public CliContext(SessionStore store, ToolRegistry registry, LlmProvider provider,
                      ConfigService config, String model, int maxIterations, CliPort port,
                      LineReader reader) {
        this(store, registry, provider, config, model, maxIterations, port, ReplLoop.fromLineReader(reader), null);
    }

    /** 测试缝：直接注入行输入来源；单测不构造 JLine 终端（真终端会抢 System.in，污染 surefire 管道）。 */
    public CliContext(SessionStore store, ToolRegistry registry, LlmProvider provider,
                      ConfigService config, String model, int maxIterations, CliPort port,
                      ReplLoop.LineInput input) {
        this(store, registry, provider, config, model, maxIterations, port, input, null);
    }

    /** 生产装配 + 钩子：无参的两构式委托到这里，README 对外仍以 8 参（无钩子）为主。 */
    public CliContext(SessionStore store, ToolRegistry registry, LlmProvider provider,
                      ConfigService config, String model, int maxIterations, CliPort port,
                      LineReader reader, HookDispatcher hooks) {
        this(store, registry, provider, config, model, maxIterations, port, ReplLoop.fromLineReader(reader), hooks);
    }

    /** 测试缝 + 钩子：单测可直接注入行输入来源与钩子派发器。 */
    public CliContext(SessionStore store, ToolRegistry registry, LlmProvider provider,
                      ConfigService config, String model, int maxIterations, CliPort port,
                      ReplLoop.LineInput input, HookDispatcher hooks) {
        this.store = store;
        this.registry = registry;
        this.provider = provider;
        this.config = config;
        this.model = model;
        this.maxIterations = maxIterations;
        this.port = port;
        this.input = input;
        this.hooks = hooks;
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
     * 再以显式会话绑 {@link AgentLoop}（每轮落盘）。
     *
     * @param session        会话（新建或已从存储加载），不可为 null
     * @param replayHistory  是否先把该会话历史回放为与实时一致的事件流
     * @return 待运行的 ReplLoop；由命令调用 {@code run()} 消费完整交互循环
     */
    public ReplLoop enterRepl(Session session, boolean replayHistory) {
        if (replayHistory) {
            SessionReplayer.replay(session, port);
        }
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, registry, model, maxIterations, store, session, hooks);
        return new ReplLoop(port, agent::run, input, session.id());
    }
}