package com.learn.mycc.app;

import com.learn.mycc.core.context.IocContainer;

/** v1 启动器（M6 完善为完整 REPL）：按基础包装配 IOC 容器并暴露之。 */
public final class MyccApplication {

    private final String basePackage;
    private final IocContainer iocContainer;

    public MyccApplication(String basePackage) {
        this.basePackage = basePackage;
        this.iocContainer = assemble();
    }

    public MyccApplication() {
        this("com.learn.mycc");
    }

    public IocContainer getIocContainer() {
        return iocContainer;
    }

    private IocContainer assemble() {
        IocContainer container = IocContainer.create();
        container.register(this.basePackage);
        container.start();
        return container;
    }
}
