package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个组件类为可调用的技能（v2 启用）。
 * 技能是一段结构化指令/提示词包：会话开始注入其 name+description 供 LLM 判断，
 * 需要时经 {@code invoke_skill} 工具按 name 激活并拿到 instructions 执行。
 * 技能类本身应为 {@code @Component}，随容器扫描被 {@code SkillRegistry}
 * （BeanPostProcessor）自动捕获。name 全局唯一。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Skill {

    /** 技能名，全局唯一，作为 {@code invoke_skill} 的精确匹配标识。 */
    String name();

    /** 技能能力描述，注入技能目录供 LLM 判断何时使用。 */
    String description();

    /** 技能指令文本，invoke 时返回给 LLM 按指示执行。 */
    String instructions();

    /** 触发条件（描述性文本，辅助 LLM 判断激活时机）；可为空串。 */
    String trigger() default "";
}