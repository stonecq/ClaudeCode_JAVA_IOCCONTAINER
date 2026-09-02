package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 工具方法参数的元数据：描述与是否必填，用于生成 JSON Schema。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

    /**
     * 参数说明，注入 Schema 对应参数的 description，用于指导 LLM 如何填值。
     * 默认空字符串表示不写 description；允许为空。
     */
    String description() default "";

    /**
     * 是否必填：为 true 时该参数会进入 JSON Schema 的 required 数组。
     * 默认 true（工具参数默认必须由 LLM 提供，除非显式置为 false 表示可选）。
     */
    boolean required() default true;
}
