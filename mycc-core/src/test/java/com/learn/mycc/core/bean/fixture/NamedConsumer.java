package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Named;
import com.learn.mycc.core.context.fixture.Greeter;

/** @Named 注入消解二义性的测试夹具：构造器按名称限定取 Greeter 实现。 */
public class NamedConsumer {

    private final Greeter greeter;

    public NamedConsumer(@Named("greeterImplB") Greeter greeter) {
        this.greeter = greeter;
    }

    public Greeter getGreeter() {
        return greeter;
    }
}