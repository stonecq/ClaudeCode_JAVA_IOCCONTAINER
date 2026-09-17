package com.learn.mycc.ui;

import com.learn.mycc.core.permission.UserConfirmation;

import java.util.ServiceLoader;

/**
 * UI 适配器：一个可插拔界面（cli / web / native …）的契约。
 * <p>UI 只依赖 {@link AgentApi}（agent 门面）与自管传输物；<b>不接触自研容器与 agent 内部 bean</b>。
 * UI 向容器提供两个"外向端口"（输出 {@link InteractionPort}、审批 {@link UserConfirmation}），
 * 由 Main 在 {@code start()} 前登记。</p>
 * <p>发现方式：{@link ServiceLoader}（各 UI 模块在 {@code META-INF/services/} 注册实现）。
 * 新增 UI = 加模块 + 实现本接口 + 注册文件，Main 与 agent 零改动。</p>
 */
public interface UiAdapter {

    /** UI 标识，对应启动参数 {@code --ui <id>}（如 "cli" / "web"）。 */
    String id();

    /**
     * 该 UI 的输出端口（agent 经它下发 {@code OutputEvent}）。实现可惰性创建并缓存；
     * 端口所需配置读 UI 自己的 {@link UiConfig}，不经 agent。
     */
    InteractionPort port();

    /**
     * 该 UI 的审批输入端口（agent 权限系统经它征求用户决定）。
     * 无交互审批能力的 UI 可返回 fail-closed 实现（{@code UnavailableUserConfirmation}）。
     */
    UserConfirmation userConfirmation();

    /**
     * 拉起该 UI：用 {@link AgentApi} 驱动对话、启动界面并阻塞至退出。
     *
     * @param agent agent 门面
     * @param args  该 UI 自己的参数（{@code --ui <id>} 已被剥离）
     */
    void start(AgentApi agent, String[] args);
}