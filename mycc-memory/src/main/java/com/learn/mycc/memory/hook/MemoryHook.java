package com.learn.mycc.memory.hook;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.memory.MemoryStorage;
import com.learn.mycc.memory.MemoryType;

import java.util.List;

/**
 * 记忆与 agent 生命周期的桥：会话开始注入记忆上下文、会话结束固化本回合记录。
 * <p>SESSION_START：把「会话全文 + USER/PROJECT 索引」追加进 system prompt 列表。
 * 索引只含 id 与一句话描述（量小），LLM 若要原文需用 {@code read_memory} 工具按 id 调取；
 * SESSION 层读取全文直接注入（会话是一次性的上下文，量级小且无需拆条）。</p>
 * <p>SESSION_END：把本回合的「用户输入 + 最终回答」作为普通文本覆盖写进会话层记忆，
 * 由 AgentLoop 以 String payload 派发；非 String / null payload 视为无内容，跳过
 * （钩子不挑 payload 类型，对其它订阅者无耦合）。</p>
 */
@Component
public class MemoryHook {

    private final MemoryStorage storage;

    @Inject
    public MemoryHook(MemoryStorage storage) {
        this.storage = storage;
    }

    /** 会话开始：按「会话全文 → 用户索引 → 项目索引」注入，空层跳过。 */
    @Hook(event = HookEventType.SESSION_START)
    public void memoryIndexHook(HookEvent event) {
        if (event == null || event.payload() == null) {
            return;
        }
        if (event.payload() instanceof List<?> rawList) {
            @SuppressWarnings("unchecked")
            List<String> systemPromptList = (List<String>) rawList;

            String sessionMemory = storage.loadSession(event.sessionId());
            if (sessionMemory != null) {
                systemPromptList.add("【会话记忆】\n" + sessionMemory);
            }
            addIndexBlock(systemPromptList, "【用户长期记忆索引】可用 read_memory 按 id 调取全文：", MemoryType.USER);
            addIndexBlock(systemPromptList, "【项目长期记忆索引】可用 read_memory 按 id 调取全文：", MemoryType.PROJECT);
        }
    }

    /** 会话结束：把本回合记录（AgentLoop 派发的 String payload）覆盖写进会话层记忆。 */
    @Hook(event = HookEventType.SESSION_END)
    public void saveTurnMemoryHook(HookEvent event) {
        if (event == null || event.sessionId() == null) {
            return;
        }
        if (event.payload() instanceof String turn) {
            storage.saveSession(event.sessionId(), turn);
        }
    }

    /** 把某一层的索引连同引导行加入 system prompt 列表；无记忆则跳过。 */
    private void addIndexBlock(List<String> systemPromptList, String header, MemoryType type) {
        String index = storage.loadIndex(type);
        if (index != null) {
            systemPromptList.add(header + "\n" + index);
        }
    }
}