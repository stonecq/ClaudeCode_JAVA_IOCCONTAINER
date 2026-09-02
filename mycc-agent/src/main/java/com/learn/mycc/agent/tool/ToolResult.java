package com.learn.mycc.agent.tool;

/**
 * 工具执行结果：关联调用 id，携带成功/失败标志与输出文本。
 * 用 record 表示不可变结果，便于在会话回填与多线程之间安全传递；
 * 由工厂方法封装成功/失败构造。
 *
 * @param callId  被回填的工具调用 id（与 LLM 返回的 tool call id 对应，非 null）
 * @param success 是否执行成功；失败时 {@code output} 为错误描述
 * @param output  工具输出文本；成功为 String.valueOf(返回值)，失败为错误信息
 */
public record ToolResult(String callId, boolean success, String output) {

    /** @return 成功结果，输出为 {@code String.valueOf} 后的返回值。 */
    public static ToolResult ok(String callId, String output) {
        return new ToolResult(callId, true, output);
    }

    /** @return 失败结果，输出为错误描述（异常消息等）。 */
    public static ToolResult failure(String callId, String output) {
        return new ToolResult(callId, false, output);
    }
}
