package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为生命周期钩子（v2 启用，本期仅定义注解，不注册不执行）。
 * 事件命名见 PRD FR-8：session_start / session_end / tool_call_before /
 * tool_call_after / error / user_prompt_submit。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Hook {

    /**
     * 订阅的事件名。
     * 取值来自 PRD FR-8 定义的事件集合（session_start / session_end / tool_call_before /
     * tool_call_after / error / user_prompt_submit）；不允许为空字符串。
     */
    String event();
}
