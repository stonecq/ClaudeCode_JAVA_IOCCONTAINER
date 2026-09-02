package com.learn.mycc.core.bean;

/**
 * Bean 初始化后的扩展点（对标 Spring 的 BeanPostProcessor）。
 * 容器每创建一个 bean 都会回调该方法，供外部在实例化完成后附加自定义行为，
 * 例如 {@link com.learn.mycc.core.tool.ToolRegistry} 据此反射扫描并注册 @Tool 方法。
 */
public interface BeanPostProcessor {

    /**
     * bean 创建（注入 + InitializingBean 回调）完成后由容器调用。
     *
     * @param bean     创建完成的实例
     * @param beanName bean 在容器中的名称
     * @return 处理后的实例；实现通常原样返回，也可替换为包装/代理对象
     */
    Object postProcessAfterInitialization(Object bean, String beanName);
}
