package com.learn.mycc.ai.model;

import java.util.Map;

/**
 * 工具描述（发给 LLM 用）：名称、描述、参数 JSON Schema。
 * <p>parameters 为 JSON Schema 结构（如 {"type":"object","properties":{...}}），
 * 服务端据此约束模型生成合法的工具参数；序列化时需转为树节点。
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {
}
