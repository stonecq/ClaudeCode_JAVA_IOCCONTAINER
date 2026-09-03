package com.learn.mycc.core.annotation;

import com.learn.mycc.core.hook.HookEventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为生命周期钩子（v2 启用）。
 * 事件由 {@link HookEventType} 枚举强类型声明，避免了手写事件字符串的拼写风险，
 * 未知事件在编译期即被拒绝（无需运行时校验）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Hook {

    /**
     * 订阅的事件类型。
     * 取值见 {@link HookEventType}（session_start / session_end / tool_call_before /
     * tool_call_after / error / user_prompt_submit）。
     */
    HookEventType event();
}