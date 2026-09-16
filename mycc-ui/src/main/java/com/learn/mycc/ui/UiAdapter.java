package com.learn.mycc.ui;

import com.learn.mycc.core.context.IocContainer;

import java.util.ServiceLoader;

/**
 * UI 适配器：一个可插拔界面（cli / web / native …）的契约。
 * <p>分工：自研容器只装配 <b>agent 领域</b>；UI 全在容器之外，经本接口接入——从容器取所需
 * agent 组件（如 {@code AgentLoop}），并自行处理界面传输（CLI 用 JLine/picocli、Web 用 Spring）。
 * 于是 UI 切换对 agent 与容器<b>无感</b>。</p>
 * <p>发现方式：{@link ServiceLoader}（各 UI 模块在 {@code META-INF/services/} 注册实现）。
 * 新增 UI = 加模块 + 实现本接口 + 注册文件，Main 与 agent 零改动。</p>
 *
 * <p>注：agent 的外向端口（输出 {@code InteractionPort}、审批 {@code UserConfirmation}）由 UI
 * 提供——待"CLI 去容器化"阶段一并纳入本接口（届时新增 {@code port()} / {@code userConfirmation()}）。</p>
 */
public interface UiAdapter {

    /** UI 标识，对应启动参数 {@code --ui <id>}（如 "cli" / "web"）。 */
    String id();

    /**
     * 拉起该 UI：从容器取所需 agent 组件、启动界面并阻塞至退出。
     *
     * @param container 已装配 agent 的自研容器
     * @param args      该 UI 自己的参数（{@code --ui <id>} 已被剥离）
     */
    void start(IocContainer container, String[] args);
}