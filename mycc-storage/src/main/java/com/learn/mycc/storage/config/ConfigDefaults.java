package com.learn.mycc.storage.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置项清单与内置默认值：全项目可配置参数的**唯一来源**。
 * <p>key 一律点号命名空间（{@code <域>.<项>}，如 {@code agent.model}、{@code compact.resultBudget}）；
 * 各模块不再自带 {@code DEFAULT_*} 常量，统一经 {@link ConfigService#get(String)} /
 * {@code getInt} / {@code getBool} / {@code getDouble} 读取（命中不到外部来源时回落到本表默认）。
 * 首次运行时 {@link ConfigService} 会据此生成 {@code ~/.mycc/config.json} 供用户修改。</p>
 */
public final class ConfigDefaults {

    /** agent 主循环与子代理共用的模型名。 */
    public static final String AGENT_MODEL = "agent.model";
    /** agent 单轮最大迭代次数。 */
    public static final String AGENT_MAX_ITERATIONS = "agent.maxIterations";
    /** 上下文超长时 reactive 压缩的最大重试次数。 */
    public static final String AGENT_MAX_REACTIVE_RETRIES = "agent.maxReactiveRetries";

    /** CLI 是否渲染思考（reasoning）块。 */
    public static final String CLI_SHOW_REASONING = "cli.showReasoning";

    /** 工作区根路径（工具/记忆/技能等的作用目录）；默认 {@code .} 即当前目录。 */
    public static final String WORKSPACE_PATH = "workpath";

    /** Web 服务监听端口。 */
    public static final String WEB_PORT = "web.port";

    /** 记忆每层条数上限，超限触发清理子代理。 */
    public static final String MEMORY_MAX_ENTRIES_PER_LAYER = "memory.maxEntriesPerLayer";

    /** 单轮工具结果总字符预算。 */
    public static final String COMPACT_RESULT_BUDGET = "compact.resultBudget";
    /** 单条工具结果整体转存的下限字符数。 */
    public static final String COMPACT_LARGE_RESULT = "compact.largeResult";
    /** 触发截断的消息条数上限。 */
    public static final String COMPACT_MAX_MESSAGES = "compact.maxMessages";
    /** 触发 micro/摘要的上下文估长上限（字符）。 */
    public static final String COMPACT_CONTEXT_LIMIT = "compact.contextLimit";
    /** micro 阶段保留的最近工具结果条数。 */
    public static final String COMPACT_MICRO_KEEP_RECENT = "compact.microKeepRecent";
    /** reactive 阶段保留的最近消息条数。 */
    public static final String COMPACT_REACTIVE_KEEP = "compact.reactiveKeep";
    /** 压缩目标系数（降到上限的此比例）。 */
    public static final String COMPACT_TARGET_RATIO = "compact.targetRatio";
    /** 大结果整体转存后保留的预览字符数。 */
    public static final String COMPACT_PREVIEW = "compact.preview";
    /** fit 阶段转存结果保留的预览字符数。 */
    public static final String COMPACT_FIT_PREVIEW = "compact.fitPreview";
    /** micro 阶段可缩短结果的最小字符数（低于此不处理）。 */
    public static final String COMPACT_MICRO_MIN = "compact.microMin";
    /** 截断时保留的头部消息数。 */
    public static final String COMPACT_HEAD_KEEP = "compact.headKeep";

    /** key → 默认值（值以字符串存放，读取时按需转 int/bool/double）。LinkedHashMap 保序。 */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(AGENT_MODEL, "deepseek-v4-flash");
        DEFAULTS.put(AGENT_MAX_ITERATIONS, "10");
        DEFAULTS.put(AGENT_MAX_REACTIVE_RETRIES, "1");
        DEFAULTS.put(CLI_SHOW_REASONING, "true");
        DEFAULTS.put(WORKSPACE_PATH, ".");
        DEFAULTS.put(WEB_PORT, "8080");
        DEFAULTS.put(MEMORY_MAX_ENTRIES_PER_LAYER, "20");
        DEFAULTS.put(COMPACT_RESULT_BUDGET, "200000");
        DEFAULTS.put(COMPACT_LARGE_RESULT, "30000");
        DEFAULTS.put(COMPACT_MAX_MESSAGES, "50");
        DEFAULTS.put(COMPACT_CONTEXT_LIMIT, "50000");
        DEFAULTS.put(COMPACT_MICRO_KEEP_RECENT, "3");
        DEFAULTS.put(COMPACT_REACTIVE_KEEP, "5");
        DEFAULTS.put(COMPACT_TARGET_RATIO, "0.8");
        DEFAULTS.put(COMPACT_PREVIEW, "2000");
        DEFAULTS.put(COMPACT_FIT_PREVIEW, "1000");
        DEFAULTS.put(COMPACT_MICRO_MIN, "120");
        DEFAULTS.put(COMPACT_HEAD_KEEP, "3");
    }

    private ConfigDefaults() {
    }

    /** @return 全部 key→默认值（不可变，按声明顺序）。 */
    public static Map<String, String> all() {
        return Map.copyOf(DEFAULTS);
    }

    /** @return 指定 key 的默认值；未登记则 null。 */
    public static String of(String key) {
        return DEFAULTS.get(key);
    }
}