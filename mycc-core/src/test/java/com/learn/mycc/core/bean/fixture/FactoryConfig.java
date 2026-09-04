package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Bean;
import com.learn.mycc.core.annotation.Configuration;
import com.learn.mycc.core.context.fixture.Greeter;
import com.learn.mycc.core.context.fixture.GreeterImplA;

/**
 * @Configuration 测试夹具：验证 @Bean 工厂方法被注册为 Bean 定义、
 * 形参按类型注入、以及 @Bean.destroyMethod 在关闭时反射调用。
 */
@Configuration
public class FactoryConfig {

    @Bean
    public Greeter greeter() {
        return new GreeterImplA();
    }

    @Bean
    public DepConsumer depConsumer(FieldDep dep) {
        return new DepConsumer(dep);
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedService managedService() {
        return new ManagedService();
    }
}