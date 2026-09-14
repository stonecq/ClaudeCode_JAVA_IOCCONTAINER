package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记一个方法为可被 agent 调用的工具；方法所在类需为 @Component 以便容器注册。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {

    /**
     * 工具名，全局唯一，供 LLM 在 tool use 时作为函数名精确调用。
     * 值不允许为 null 或空白，且不可与其它工具重名，否则注册时抛异常。
     */
    String name();

    /**
     * 工具功能描述，会注入 LLM 的工具 Schema（parameters.description）。
     * 该描述是 LLM 决定何时使用本工具的主要依据，应清晰说明用途与适用场景。
     */
    String description();

    /**
     * 工具风险等级，默认 {@link ToolRisk#LOW}。HIGH 工具（bash、写文件等）
     * 的调用默认触发审批，规则文件可按工具覆盖。
     */
    ToolRisk risk() default ToolRisk.LOW;

    /**
     * 是否禁止子代理使用：默认 false（默认允许子代理调用）。
     * 标 true 的工具不会进入子代理的工具集——subagent 工具本身应置 true 以防
     * 递归，memory/skill/plan 等"心智/协调"类工具通常也应置 true 让子代理专注执行。
     */
    boolean subagentExcluded() default false;

    /**
     * 是否禁止"记忆清理子代理"使用：默认 true（默认排除，不参与记忆清理）。
     * 记忆相关工具（load_index / read_memory / save_memory / delete_memory）应显式置
     * false，使其进入清理子代理的工具集——该判断独立于 {@link #subagentExcluded}，
     * 因此这些工具可同时"对普通子代理隐藏、对清理子代理开放"。
     */
    boolean memoryCleanAgentExclude() default true;
}
