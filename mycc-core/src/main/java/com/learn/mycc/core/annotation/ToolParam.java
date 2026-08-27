package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 工具方法参数的元数据：描述与是否必填，用于生成 JSON Schema。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

    /** 参数说明，注入 Schema 的 description。 */
    String description() default "";

    /** 是否必填。 */
    boolean required() default true;
}
