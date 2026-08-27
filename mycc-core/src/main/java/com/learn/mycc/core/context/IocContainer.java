package com.learn.mycc.core.context;

import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import com.learn.mycc.core.bean.BeanPostProcessor;
import com.learn.mycc.core.scan.AnnotationScanner;
import com.learn.mycc.core.tool.ToolRegistry;

import java.util.List;

/**
 * IoC 容器门面：注册 BeanDefinition、启动（预创建全部单例）、按类型取 Bean、统一关闭。
 * 具体实现 {@link DefaultIocContainer} 委托 {@link BeanFactory}。
 */
public interface IocContainer {

    void register(BeanDefinition... definitions);

    void register(String basePackage);

    void addBeanPostProcessor(BeanPostProcessor processor);

    void start();

    <T> T getBean(Class<T> type);

    <T> List<T> getBeansOfType(Class<T> type);

    /** 容器内置的工具注册表（自动捕获所有 @Tool 方法）。 */
    ToolRegistry getToolRegistry();

    void close();

    static IocContainer create() {
        return new DefaultIocContainer();
    }
}

final class DefaultIocContainer implements IocContainer {

    private final BeanFactory beanFactory = new BeanFactory();
    private final AnnotationScanner scanner = new AnnotationScanner(Thread.currentThread().getContextClassLoader());
    private final ToolRegistry toolRegistry = new ToolRegistry();

    DefaultIocContainer() {
        beanFactory.addBeanPostProcessor(toolRegistry);
    }

    @Override
    public void register(BeanDefinition... definitions) {
        beanFactory.register(definitions);
    }

    @Override
    public void register(String basePackage) {
        beanFactory.register(scanner.scanBeanDefinitions(basePackage).toArray(BeanDefinition[]::new));
    }

    @Override
    public void addBeanPostProcessor(BeanPostProcessor processor) {
        beanFactory.addBeanPostProcessor(processor);
    }

    @Override
    public void start() {
        beanFactory.preInstantiateSingletons();
    }

    @Override
    public <T> T getBean(Class<T> type) {
        return beanFactory.getBean(type);
    }

    @Override
    public <T> List<T> getBeansOfType(Class<T> type) {
        return beanFactory.getBeansOfType(type);
    }

    @Override
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    @Override
    public void close() {
        beanFactory.close();
    }
}
