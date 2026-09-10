package com.learn.mycc.skill.tool;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.skill.SkillRegistry;

/**
 * 技能调用工具：把 {@code invoke_skill} 暴露给 LLM。
 * <p>技能目录已随会话开始注入，LLM 依据它选技能名；invoke 后返回该技能的
 * 指令文本，由 LLM 按指示执行。未注册的技能名抛 {@code MyccException}，
 * 经工具执行器转 failure 回填给 LLM。</p>
 */
@Component
public class SkillTools {

    private final SkillRegistry registry;

    @Inject
    public SkillTools(SkillRegistry registry) {
        this.registry = registry;
    }

    /** 激活一个技能，返回其指令文本供 LLM 按指示执行。 */
    @Tool(name = "invoke_skill", description = "激活一个技能，返回其指令文本供按指示执行（可用技能目录已随会话开始注入）", subagentExcluded = true)
    public String invokeSkill(@ToolParam(description = "技能名，取自注入的技能目录") String name) {
        return registry.get(name).getInstructions();
    }
}