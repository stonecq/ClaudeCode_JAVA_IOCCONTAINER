package com.learn.mycc.ui;

/**
 * 会话列表项视图：供 UI 展示，避免暴露 agent 内部的 {@code Session} 类型。
 *
 * @param id           会话 id
 * @param title        展示标题（末条用户消息）
 * @param lastModified 最后修改时间（epoch 毫秒）
 */
public record SessionView(String id, String title, long lastModified) {
}