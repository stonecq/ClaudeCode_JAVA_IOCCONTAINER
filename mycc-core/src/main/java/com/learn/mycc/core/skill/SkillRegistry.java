package com.learn.mycc.core.skill;

import com.learn.mycc.core.annotation.Skill;
import com.learn.mycc.core.bean.BeanPostProcessor;
import com.learn.mycc.core.exception.MyccException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能注册表：实现 {@link BeanPostProcessor}，在 bean 创建完成后反射读取
 * 其类上的 {@link Skill} 注解并注册。与 {@link com.learn.mycc.core.tool.ToolRegistry}、
 * {@link com.learn.mycc.core.hook.HookRegistry} 同构——「被动捕获」：只要把本实例
 * add 为后置处理器，任何组件后续创建的 {@code @Skill} 类都会被自动收集，与 IoC 容器
 * 天然解耦。skillsByName 用 LinkedHashMap 保持注册顺序。
 */
public final class SkillRegistry implements BeanPostProcessor {

    /** 技能名 → 技能定义映射；LinkedHashMap 使 getAll 输出与注册顺序一致。 */
    private final Map<String, SkillDefinition> skillsByName = new LinkedHashMap<>();

    /**
     * 容器回调：读取 bean 类上的 {@link Skill} 注解并注册；未标注的类被忽略。
     *
     * @param bean     创建完成的 bean 实例
     * @param beanName bean 名称（本实现不使用）
     * @return 原 bean 实例（本后置处理器不改造实例）
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Skill skill = bean.getClass().getAnnotation(Skill.class);
        if (skill != null) {
            register(new SkillDefinition(skill.name(), skill.description(), skill.instructions(), skill.trigger()));
        }
        return bean;
    }

    /**
     * 注册单个技能定义。
     *
     * @param definition 技能定义，不允许为 null
     * @throws MyccException 名称为空或与已有技能重名时抛出
     */
    public void register(SkillDefinition definition) {
        String name = definition.getName();
        if (name == null || name.isBlank()) {
            throw new MyccException("技能名称不能为空: " + definition);
        }
        if (skillsByName.putIfAbsent(name, definition) != null) {
            throw new MyccException("重复注册技能: " + name);
        }
    }

    /** @return 已注册技能快照（按注册顺序，不可变） */
    public List<SkillDefinition> getAll() {
        return List.copyOf(skillsByName.values());
    }

    /**
     * 按名称取技能。
     *
     * @param name 技能名
     * @return 对应技能定义
     * @throws MyccException 技能未注册时抛出
     */
    public SkillDefinition get(String name) {
        SkillDefinition definition = skillsByName.get(name);
        if (definition == null) {
            throw new MyccException("未注册技能: " + name);
        }
        return definition;
    }
}