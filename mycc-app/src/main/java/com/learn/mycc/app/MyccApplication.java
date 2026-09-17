package com.learn.mycc.app;

import com.learn.mycc.core.context.IocContainer;

import java.util.List;

/**
 * IoC 装配根：只装配 <b>agent 领域</b>包（不含 cli/web/app 等 UI 层）。
 * <p>UI 组件由各 {@code UiAdapter} 自建；agent 的外向端口（{@code InteractionPort} /
 * {@code UserConfirmation}）也由 UI 适配器提供，由 {@code Main} 在 start 前登记。</p>
 */
public final class MyccApplication {

    /** agent 领域包；UI 层（cli/web/app）刻意不在内，UI 切换对容器无感。 */
    private static final List<String> DOMAIN_PACKAGES = List.of(
            "com.learn.mycc.core",
            "com.learn.mycc.ui",
            "com.learn.mycc.ai",
            "com.learn.mycc.storage",
            "com.learn.mycc.agent",
            "com.learn.mycc.tools",
            "com.learn.mycc.hooks",
            "com.learn.mycc.memory",
            "com.learn.mycc.skill",
            "com.learn.mycc.planning",
            "com.learn.mycc.subagent",
            "com.learn.mycc.compact");

    /** 装配完成的 IOC 容器；构造后即就绪，仅可读、不可再注册。 */
    private final IocContainer iocContainer;

    public MyccApplication() {
        this.iocContainer = assemble();
    }

    /** @return 已装配完成的 IOC 容器。 */
    public IocContainer getIocContainer() {
        return iocContainer;
    }

    /** 启动容器：预创建全部单例（prototype 按需）。 */
    public void start() {
        iocContainer.start();
    }

    /** 创建容器并注册领域包内组件。 */
    private IocContainer assemble() {
        IocContainer container = IocContainer.create();
        DOMAIN_PACKAGES.forEach(container::register);
        return container;
    }
}