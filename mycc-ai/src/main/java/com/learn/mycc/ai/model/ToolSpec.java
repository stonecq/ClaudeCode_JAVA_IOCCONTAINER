package com.learn.mycc.ai.model;

import java.util.Map;

/** 工具描述（发给 LLM 用）：名称、描述、参数 JSON Schema。 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {
}
