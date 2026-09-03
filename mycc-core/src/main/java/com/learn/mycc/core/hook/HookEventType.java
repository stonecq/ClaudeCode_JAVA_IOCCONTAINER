package com.learn.mycc.core.hook;

import com.learn.mycc.core.exception.MyccException;

/**
 * 钩子事件类型：定义 agent 生命周期中可订阅的全部事件。
 * 枚举值以 {@link #eventName()} 与 {@link com.learn.mycc.core.annotation.Hook} 注解的
 * {@code event()} 字符串一一对应，作为「注解字符串 → 强类型事件」的映射依据，
 * 避免在派发代码中散落魔法字符串。
 */
public enum HookEventType {

    /** 会话开始：一次 {@code run} 调用开始时触发。 */
    SESSION_START("session_start"),

    /** 会话结束：一次 {@code run} 调用结束时触发（含异常与提前结束）。 */
    SESSION_END("session_end"),

    /** 工具调用前：执行某个工具前触发，payload 为工具名。 */
    TOOL_CALL_BEFORE("tool_call_before"),

    /** 工具调用后：执行某个工具后触发，payload 为工具名。 */
    TOOL_CALL_AFTER("tool_call_after"),

    /** 错误：Provider 调用失败时触发，payload 为错误信息。 */
    ERROR("error"),

    /** 用户提交：用户消息被加入会话后触发，payload 为用户输入。 */
    USER_PROMPT_SUBMIT("user_prompt_submit");

    /** 与 @Hook 注解 {@code event()} 对应的规范字符串名，用于反射扫描时映射。 */
    private final String name;

    HookEventType(String name) {
        this.name = name;
    }

    /** @return 事件名，与 @Hook 注解的 event() 字符串一致 */
    public String eventName() {
        return name;
    }

    /**
     * 按事件名解析枚举。
     *
     * @param name 事件名字符串，如 "session_start"
     * @return 对应的事件类型
     * @throws MyccException 传入未知事件名时抛出（用于启动期校验 @Hook 声明合法）
     */
    public static HookEventType fromName(String name) {
        for (HookEventType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        throw new MyccException("未知钩子事件: " + name);
    }
}
