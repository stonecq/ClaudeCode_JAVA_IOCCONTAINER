package com.learn.mycc.subagent;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.tool.ToolCallExecutor;
import com.learn.mycc.ai.model.ToolSpec;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.tool.ParameterSchemaGenerator;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigDefaults;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.ui.InteractionPort;

import java.util.List;

/**
 * 子代理运行器：以嵌套 AgentLoop 跑一个独立会话的子任务，返回最终结果文本。
 * <p>子代理工具集 = 注册表中非 {@code subagentExcluded} 的全部工具（由各 {@code @Tool}
 * 声明，而非本组件的名单）；subagent 工具自身标注排除以防递归，memory/skill/plan
 * 等"心智/协调"工具也标注排除，让子代理专注执行子任务。子代理用临时独立 Session
 * （预置系统提示、storage=null 不落盘、hooks=null 不注入记忆/技能），输出复用主
 * {@link InteractionPort}（sessionId 不同，UI 可区分）。</p>
 */
@Component
public class SubagentService {

    /** 子代理默认系统提示；调用方可覆盖。 */
    static final String DEFAULT_SUBAGENT_PROMPT = "你是子代理，专注执行被派发的子任务，直接给出可用的结果。";

    private final LlmProvider provider;
    private final ToolRegistry toolRegistry;
    private final InteractionPort port;
    private final ConfigService config;

    @Inject
    public SubagentService(LlmProvider provider, ToolRegistry toolRegistry, InteractionPort port, ConfigService config) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.port = port;
        this.config = config;
    }

    /**
     * 派发一个子任务给子代理执行（默认工具集：非 {@code subagentExcluded} 的工具）。
     *
     * @param task         子任务指令（作为子代理用户消息）
     * @param systemPrompt 子代理系统提示；null/空白时用默认提示词
     * @return 子代理最终回答文本
     */
    public String run(String task, String systemPrompt) {
        return run(task, systemPrompt, null);
    }

    /**
     * 派发一个子任务给子代理执行。
     *
     * @param task         子任务指令（作为子代理用户消息）
     * @param systemPrompt 子代理系统提示；null/空白时用默认提示词
     * @param allowedTools 指定工具集；非空时子代理只能使用这些名字的工具（忽略
     *                     {@code subagentExcluded}，供专用清理子代理装配 memory 工具），
     *                     null/空时沿用默认排除逻辑（非 excluded 的工具）
     * @return 子代理最终回答文本
     */
    public String run(String task, String systemPrompt, List<String> allowedTools) {
        String prompt = (systemPrompt == null || systemPrompt.isBlank()) ? DEFAULT_SUBAGENT_PROMPT : systemPrompt;
        ParameterSchemaGenerator schemaGen = new ParameterSchemaGenerator();
        // 子代理工具集：指定名单则按名取（覆盖排除），否则排除各 @Tool 声明禁止子代理使用的工具
        List<ToolSpec> subTools = toolRegistry.getAll().stream()
                .filter(definition -> allowedTools == null
                        ? !definition.isSubagentExcluded()
                        : allowedTools.contains(definition.getName()))
                .map(definition -> new ToolSpec(definition.getName(), definition.getDescription(),
                        schemaGen.generate(definition.getMethod())))
                .toList();

        Session subSession = Session.create();
        subSession.addMessage(Message.system(prompt));

        String model = config.getString(ConfigDefaults.AGENT_MODEL);
        int maxIterations = config.getInt(ConfigDefaults.AGENT_MAX_ITERATIONS);
        AgentLoop subAgent = new AgentLoop(port, provider, new ToolCallExecutor(toolRegistry),
                subTools, model, maxIterations, null, subSession, null);
        return subAgent.run(task);
    }
}