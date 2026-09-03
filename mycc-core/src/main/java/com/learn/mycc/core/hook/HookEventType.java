package com.learn.mycc.core.hook;

/**
 * 钩子事件类型：定义 agent 生命周期中可订阅的全部事件。
 * {@link com.learn.mycc.core.annotation.Hook} 注解直接以本枚举声明订阅事件，
 * 事件身份是枚举常量本身，字符串名仅用于日志可读性（见 {@link #eventName()}）。
 */
public enum HookEventType {

    /** 会话开始：一次 {@code run} 调用开始时触发。 */
    SESSION_START("session_start"),

    /** 会话结束：一次 {@code run} 调用结束时触发（含异常与提前结束）。 */
    SESSION_END("session_end"),

    /** 工具调用前：执行某个工具前触发，payload 为工具调用（ToolCall），订阅者可否决。 */
    TOOL_CALL_BEFORE("tool_call_before"),

    /** 工具调用后：执行某个工具后触发，payload 为工具调用（ToolCall）。 */
    TOOL_CALL_AFTER("tool_call_after"),

    /** 错误：Provider 调用失败时触发，payload 为错误信息。 */
    ERROR("error"),

    /** 用户提交：用户消息被加入会话后触发，payload 为用户输入。 */
    USER_PROMPT_SUBMIT("user_prompt_submit");

    /** 规范的日志用名（小写下划线风格），与枚举常量一一对应。 */
    private final String name;

    HookEventType(String name) {
        this.name = name;
    }

    /** @return 事件名，用于日志等人类可读输出（如 tool_call_before） */
    public String eventName() {
        return name;
    }
}