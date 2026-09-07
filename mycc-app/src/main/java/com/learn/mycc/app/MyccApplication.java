package com.learn.mycc.app;

import com.learn.mycc.core.context.IocContainer;

/**
 * IoC 装配根：只负责「创建容器 + 注册组件」，显式 {@link #start()} 触发单例预创建。
 * <p>
 * 职责边界：不掺入任何具体 UI 或业务逻辑。构造即 create + register（扫描根包下的 @Component
 * 并展开 @Configuration 的 @Bean 工厂方法）；装配结果经 {@link #getIocContainer()} 取用，
 * 上层（Main）在准备就绪后调用 {@link #start()}，便于先注册额外 Bean 再启动。
 */
public final class MyccApplication {

    /** 容器扫描的根包名；决定 register 时自动注册哪些组件，如 "com.learn.mycc"。 */
    private final String basePackage;
    /** 装配完成的 IOC 容器；构造结束后即就绪，仅可读、不可再注册。 */
    private final IocContainer iocContainer;

    /**
     * 以指定根包装配 IOC 容器。
     *
     * @param basePackage 扫描/注册组件的根包名（如 "com.learn.mycc"），不允许为 null 或空串
     */
    public MyccApplication(String basePackage) {
        this.basePackage = basePackage;
        this.iocContainer = assemble();
    }

    /** 无参构造：默认以根包 "com.learn.mycc" 装配容器，方便 API 侧直接 new。 */
    public MyccApplication() {
        this("com.learn.mycc");
    }

    /**
     * 返回已装配完成的 IOC 容器。
     *
     * @return 就绪的容器，非 null；供上层注册额外 Bean、获取 ToolRegistry 等
     */
    public IocContainer getIocContainer() {
        return iocContainer;
    }

    /**
     * 启动容器：预创建全部单例（依赖递归创建；prototype 按需创建）。
     * 拆分自构造器——构造仅负责「创建 + 注册」，显式 start 让装配根（Main）掌控
     * 启动时机，也为上层先注册额外 Bean 再启动留出窗口。
     */
    public void start() {
        iocContainer.start();
    }

    /**
     * 完成容器的装配：创建 → 按包注册组件。构造器专用；启动由 {@link #start()} 显式触发。
     *
     * @return 装配完成的容器，尚未启动
     */
    private IocContainer assemble() {
        IocContainer container = IocContainer.create();
        container.register(this.basePackage);
        return container;
    }
}
