package com.learn.mycc.app;

import com.learn.mycc.core.context.IocContainer;

/**
 * v1 启动器（M6 完善为完整 REPL）：按基础包装配 IOC 容器并暴露给上层使用。
 * <p>
 * 职责边界：只负责「装配容器」这一件事，不掺入任何具体 UI 或业务逻辑。
 * 装配逻辑收敛到 {@link #assemble()}，构造后即可通过 {@link #getIocContainer()} 取用，
 * 便于 AgentDemoApp 等上层在启动时自定义装配结果。
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
     * 完成容器的初始化三部曲：创建 → 按包注册组件 → 启动。
     * 拆出独立方法是为让构造器简洁，未来追加更复杂的装配顺序也更易集中维护。
     *
     * @return 装配并启动完成的容器
     */
    private IocContainer assemble() {
        IocContainer container = IocContainer.create();
        container.register(this.basePackage);
        // start() 触发组件实例化与后置处理，失败会抛出异常并中止启动
        container.start();
        return container;
    }
}
