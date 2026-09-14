package com.learn.mycc.memory;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.exception.MyccException;

/**
 * 长期记忆工具：暴露给 LLM 的 {@code load_index / read_memory / save_memory / delete_memory}。
 * <p>承接「看索引 → 按 id 调取全文 → 修改/新增」的记忆使用闭环：会话开始注入的索引
 * 只带 id 与一句话描述，LLM 依据它决定调取哪条、写入什么；USER/PROJECT 层由此可被
 * LLM 主动维护。SESSION 层是系统按回合自动写入的整体记忆，不开放给工具修改。</p>
 * <p>{@code load_index} 供"索引未随会话注入"的场景（如记忆清理子代理）主动拉取索引；
 * 清理子代理由 {@code @Tool.memoryCleanAgentExclude=false} 声明可用工具。</p>
 */
@Component
public class MemoryTools {

    private final MemoryStorage storage;

    @Inject
    public MemoryTools(MemoryStorage storage) {
        this.storage = storage;
    }

    @Tool(name = "load_index", description = "按层级读取长期记忆索引（索引未随会话注入时用；返回该层全部条目 id 与描述）",
            subagentExcluded = true, memoryCleanAgentExclude = false)
    public String loadIndex(@ToolParam(description = "记忆层级：USER(用户全局偏好) 或 PROJECT(当前项目)") MemoryType type) {
        requireNotSession(type);
        String index = storage.loadIndex(type);
        return type.name() + ":\n" + (index == null ? "（该层暂无记忆）" : index);
    }

    /** 读取一条记忆全文；无该 id 时返回"未找到"提示而非空串，便于 LLM 区分"没有"。 */
    @Tool(name = "read_memory", description = "按层级与 id 读取一条长期记忆全文（索引已随会话开始注入，可据此选 id）",
            subagentExcluded = true, memoryCleanAgentExclude = false)
    public String readMemory(@ToolParam(description = "记忆层级：USER(用户全局偏好) 或 PROJECT(当前项目)") MemoryType type,
                             @ToolParam(description = "记忆 id，取自注入的索引") String id) {
        requireNotSession(type);
        String content = storage.load(id, type);
        return content == null ? "未找到记忆: " + id : content;
    }

    /** 新增或更新一条长期记忆；同 id 已存在则覆盖内容并刷新索引描述。 */
    @Tool(name = "save_memory", description = "新增或更新一条长期记忆（USER 或 PROJECT），id 已存在时覆盖",
            subagentExcluded = true, memoryCleanAgentExclude = false)
    public String saveMemory(@ToolParam(description = "记忆层级：USER(用户全局偏好) 或 PROJECT(当前项目)") MemoryType type,
                             @ToolParam(description = "记忆 id，英文短横线 slug 命名，如 user-preferences") String id,
                             @ToolParam(description = "一句话描述，展示在索引中供快速判断") String description,
                             @ToolParam(description = "记忆正文内容") String content) {
        requireNotSession(type);
        storage.save(id, description, content, type);
        return "已保存: " + id;
    }

    /** 删除一条长期记忆并同步从索引移除。 */
    @Tool(name = "delete_memory", description = "删除一条长期记忆，并从索引移除",
            subagentExcluded = true, memoryCleanAgentExclude = false)
    public String deleteMemory(@ToolParam(description = "记忆层级：USER(用户全局偏好) 或 PROJECT(当前项目)") MemoryType type,
                               @ToolParam(description = "记忆 id") String id) {
        requireNotSession(type);
        return storage.delete(id, type) ? "已删除: " + id : "未找到记忆: " + id;
    }

    /** 会话层记忆由系统回合钩子自动维护，拒绝工具直接修改。 */
    private void requireNotSession(MemoryType type) {
        if (type == MemoryType.SESSION) {
            throw new MyccException("会话层记忆由系统自动维护，请勿用工具修改");
        }
    }
}