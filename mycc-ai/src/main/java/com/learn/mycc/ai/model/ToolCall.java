package com.learn.mycc.ai.model;

/** LLM 发起的工具调用：工具名 + JSON 格式的参数（arguments）。 */
public record ToolCall(String id, String name, String arguments) {
}
