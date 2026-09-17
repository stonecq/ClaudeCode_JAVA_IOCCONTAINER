package com.learn.mycc.ui;

/**
 * 消息视图：供 UI 渲染历史，避免暴露 agent 内部的 {@code Message} 类型。
 *
 * @param role 角色名（USER / ASSISTANT / TOOL / SYSTEM）
 * @param text 正文
 */
public record MessageView(String role, String text) {
}