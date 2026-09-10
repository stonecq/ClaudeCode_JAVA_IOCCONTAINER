package com.learn.mycc.skill.hook;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.skill.SkillDefinition;
import com.learn.mycc.core.skill.SkillRegistry;

import java.util.List;

/**
 * 技能与 agent 生命周期的桥：会话开始时把可用技能的目录（name+description）
 * 追加进 system prompt 列表，供 LLM 判断是否要 {@code invoke_skill} 激活。
 * 与 {@code MemoryHook} 的注入通道同构（payload 为可变 {@code List<String>}）；
 * 无技能注册时不动 payload，保持历史行为一致。
 */
@Component
public class SkillHook {

    private final SkillRegistry registry;

    @Inject
    public SkillHook(SkillRegistry registry) {
        this.registry = registry;
    }

    /** 会话开始：注入技能目录；无技能则跳过。 */
    @Hook(event = HookEventType.SESSION_START)
    public void skillCatalogHook(HookEvent event) {
        if (event == null || event.payload() == null) {
            return;
        }
        if (event.payload() instanceof List<?> rawList) {
            @SuppressWarnings("unchecked")
            List<String> systemPromptList = (List<String>) rawList;
            List<SkillDefinition> skills = registry.getAll();
            if (skills.isEmpty()) {
                return;
            }
            StringBuilder catalog = new StringBuilder("【可用技能】需要时调用 invoke_skill(name) 激活：");
            for (SkillDefinition skill : skills) {
                catalog.append("\n- ").append(skill.getName()).append(": ").append(skill.getDescription());
            }
            systemPromptList.add(catalog.toString());
        }
    }
}