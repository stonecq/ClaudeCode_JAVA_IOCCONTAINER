package com.learn.mycc.core.bean;

/** Bean 初始化后的扩展点：容器每创建一个 bean 都会回调，可用于注册注解元数据（如 ToolRegistry）。 */
public interface BeanPostProcessor {

    Object postProcessAfterInitialization(Object bean, String beanName);
}
