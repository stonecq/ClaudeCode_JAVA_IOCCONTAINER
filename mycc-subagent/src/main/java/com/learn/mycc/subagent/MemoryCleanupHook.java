package com.learn.mycc.subagent;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.memory.MemoryStorage;
import com.learn.mycc.memory.MemoryType;
import com.learn.mycc.storage.config.ConfigService;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆清理钩子：会话结束（SESSION_END）时检测 USER/PROJECT 层记忆条数是否超限，
 * 超限则启动**专用清理子代理**整理（工具集由各 {@code @Tool.memoryCleanAgentExclude=false}
 * 声明推导，覆盖 subagentExcluded）。
 * <p>编排放本模块（subagent）而非 memory：memory 保持纯机制层；清理既要用
 * {@link MemoryStorage} 判超限、又要用 {@link SubagentService} 启动子代理，故收敛在此。</p>
 * <p>清理子代理自身不经本 hook——SubagentService 构造的子代理 hooks=null，不产生
 * SESSION_END，天然避免递归清理。</p>
 */
@Component
public class MemoryCleanupHook {

    /** 每层记忆条数上限的默认值；可用配置键 {@code memory.maxEntriesPerLayer} 覆盖。 */
    static final String DEFAULT_MAX_ENTRIES = "20";
    /** 清理子代理专属系统提示，收敛其职责为"只做记忆整理"。 */
    static final String CLEANUP_PROMPT = "你是记忆整理代理，只做记忆整理，不执行其它任务。";

    private final MemoryStorage memory;
    private final SubagentService subagents;
    private final ToolRegistry toolRegistry;
    private final ConfigService config;

    @Inject
    public MemoryCleanupHook(MemoryStorage memory, SubagentService subagents, ToolRegistry toolRegistry, ConfigService config) {
        this.memory = memory;
        this.subagents = subagents;
        this.toolRegistry = toolRegistry;
        this.config = config;
    }

    /** 会话结束：逐层检查超限并启动清理子代理；无超限层或上下文缺失则静默返回。 */
    @Hook(event = HookEventType.SESSION_END)
    public void cleanupMemoryHook(HookEvent event) {
        if (event == null || event.sessionId() == null) {
            return;
        }
        int limit = Integer.parseInt(config.get("memory.maxEntriesPerLayer", DEFAULT_MAX_ENTRIES));
        List<String> over = new ArrayList<>();
        for (MemoryType type : new MemoryType[]{MemoryType.USER, MemoryType.PROJECT}) {
            int count = memory.entryCount(type);
            if (count > limit) {
                over.add(type.name() + ":" + count);
            }
        }
        if (over.isEmpty()) {
            return;
        }
        // 清理子代理工具集由 @Tool(memoryCleanAgentExclude) 声明推导，非硬编码名单
        List<String> cleanupTools = toolRegistry.getAll().stream()
                .filter(definition -> !definition.isMemoryCleanAgentExclude())
                .map(definition -> definition.getName())
                .toList();
        String task = "当前记忆层超限（每层上限 " + limit + " 条）：" + String.join("、", over)
                + "。请用 load_index 读取各层索引了解现有条目，用 read_memory 查看内容，"
                + "delete_memory 删除无用/过期条目，save_memory 把冗余条目合并为高质条目，"
                + "把每层条目数降到上限以下。";
        subagents.run(task, CLEANUP_PROMPT, cleanupTools);
    }
}