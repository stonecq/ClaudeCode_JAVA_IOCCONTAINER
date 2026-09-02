package com.learn.mycc.ai.model;

/**
 * LLM 发起的工具调用：工具名 + JSON 格式的参数（arguments）。
 * <p>id 为服务端下发的调用标识，工具执行完毕后需以 TOOL 角色消息回填该 id；
 * arguments 为 JSON 字符串，调用方负责反序列化为具体工具参数。
 * 三个字段均不应为 null。
 */
public record ToolCall(String id, String name, String arguments) {
}
