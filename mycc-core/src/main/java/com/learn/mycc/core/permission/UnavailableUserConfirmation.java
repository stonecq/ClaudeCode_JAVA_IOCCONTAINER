package com.learn.mycc.core.permission;

import com.learn.mycc.core.annotation.Component;

/**
 * 审批不可用的兜底实现（fail-closed）：prompt 恒返
 * {@link UserConfirmation.ConfirmChoice#UNAVAILABLE}，由调用方按拒绝降级。
 * <p>供无 UI / 裸容器装配时成为 {@link UserConfirmation} 的唯一候选；
 * 完整 UI 环境由 {@code CliConfig} 的 @Bean 按接口返回类型精确命中并遮蔽本实现。</p>
 */
@Component
public final class UnavailableUserConfirmation implements UserConfirmation {

    @Override
    public ConfirmChoice prompt(String toolName, String description, String args) {
        return ConfirmChoice.UNAVAILABLE;
    }
}