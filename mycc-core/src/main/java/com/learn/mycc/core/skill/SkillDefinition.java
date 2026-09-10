package com.learn.mycc.core.skill;

/**
 * 技能定义：{@code @Skill} 注解的元数据快照（名称、描述、指令与触发条件）。
 * 由 {@link SkillRegistry} 在 bean 创建时从注解构建，作为技能目录展示与
 * {@code invoke_skill} 执行的统一入口；不可变类（final 字段 + 无 setter）。
 */
public final class SkillDefinition {

    /** 技能名，全局唯一，作为 invoke_skill 的精确匹配标识；不允许为 null 或空白。 */
    private final String name;

    /** 技能能力描述，注入技能目录供 LLM 判断；可为空串。 */
    private final String description;

    /** 技能指令文本，invoke 时返回给 LLM 执行；可为空串。 */
    private final String instructions;

    /** 触发条件（描述性文本）；可为空串。 */
    private final String trigger;

    /**
     * @param name         技能名，不允许为 null 或空白
     * @param description  能力描述，可为空串
     * @param instructions 指令文本，可为空串
     * @param trigger      触发条件，可为空串
     */
    public SkillDefinition(String name, String description, String instructions, String trigger) {
        this.name = name;
        this.description = description;
        this.instructions = instructions;
        this.trigger = trigger;
    }

    /** @return 技能名 */
    public String getName() {
        return name;
    }

    /** @return 技能能力描述 */
    public String getDescription() {
        return description;
    }

    /** @return 技能指令文本 */
    public String getInstructions() {
        return instructions;
    }

    /** @return 技能触发条件 */
    public String getTrigger() {
        return trigger;
    }
}