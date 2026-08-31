package com.learn.mycc.agent.tool;

/** 工具执行结果：关联调用 id，成功/失败与输出文本。 */
public record ToolResult(String callId, boolean success, String output) {

    public static ToolResult ok(String callId, String output) {
        return new ToolResult(callId, true, output);
    }

    public static ToolResult failure(String callId, String output) {
        return new ToolResult(callId, false, output);
    }
}
