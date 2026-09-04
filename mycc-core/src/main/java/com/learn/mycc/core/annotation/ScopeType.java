package com.learn.mycc.core.annotation;

/**
 * Bean 作用域枚举：决定实例在容器内的缓存策略。
 * {@code SINGLETON} 为容器内唯一实例、按类型共享；{@code PROTOTYPE}
 * 每次取用都新建独立实例，不入单例缓存（用于会话级对象，如 AgentLoop）。
 */
public enum ScopeType {
    SINGLETON,
    PROTOTYPE
}