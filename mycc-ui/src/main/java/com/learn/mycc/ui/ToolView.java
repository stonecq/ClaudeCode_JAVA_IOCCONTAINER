package com.learn.mycc.ui;

/**
 * 工具视图：供 UI 展示可用工具，避免暴露 agent 内部的 {@code ToolDefinition} 类型。
 *
 * @param name        工具名
 * @param description 工具描述
 */
public record ToolView(String name, String description) {
}