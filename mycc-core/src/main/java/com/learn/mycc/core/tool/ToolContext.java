package com.learn.mycc.core.tool;

/**
 * 工具调用上下文：把"当前会话"等运行期信息注入工具方法。
 * 工具方法可声明一个 {@code ToolContext} 参数——Schema 生成时该参数被跳过（不暴露给 LLM），
 * 执行时由 {@link com.learn.mycc.agent.tool.ToolCallExecutor} 注入当前会话 id。
 * 使无状态的 @Tool 组件也能按会话区分状态（如计划/记忆的按会话存取）。
 */
public record ToolContext(String sessionId) {
}