package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记一个方法为可被 agent 调用的工具；方法所在类需为 @Component 以便容器注册。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {

    /** 工具名，全局唯一，供 LLM 调用。 */
    String name();

    /** 工具功能描述，会注入 LLM 的工具 Schema。 */
    String description();
}
