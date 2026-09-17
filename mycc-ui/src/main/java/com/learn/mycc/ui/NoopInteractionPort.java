package com.learn.mycc.ui;

import com.learn.mycc.core.annotation.Component;

/**
 * 交互端口兜底实现：丢弃所有输出事件。
 * <p>供无 UI / 裸容器装配时成为 {@link InteractionPort} 的唯一候选（对称于
 * {@code UnavailableUserConfirmation}）；完整 UI 环境由 {@code UiAdapter} 提供端口、
 * 在 Main {@code start()} 前登记，遮蔽本实现。</p>
 */
@Component
public final class NoopInteractionPort implements InteractionPort {

    @Override
    public void onEvent(OutputEvent event) {
        // 无 UI：丢弃输出
    }
}