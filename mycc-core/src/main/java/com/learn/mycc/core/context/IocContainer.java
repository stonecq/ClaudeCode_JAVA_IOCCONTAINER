package com.learn.mycc.core.context;

import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import com.learn.mycc.core.bean.BeanPostProcessor;
import com.learn.mycc.core.hook.HookRegistry;
import com.learn.mycc.core.scan.AnnotationScanner;
import com.learn.mycc.core.tool.ToolRegistry;

import java.util.Arrays;
import java.util.List;

/**
 * IoC 容器门面：对 {@link BeanFactory} 的简化包装，对外提供统一的
 * 注册 → 启动 → 取 Bean → 关闭 生命周期入口。
 * 具体实现 {@link DefaultIocContainer}（同文件、包私有）委托 {@link BeanFactory}，
 * 并额外持有 {@link AnnotationScanner}（包扫描注册）、{@link ToolRegistry} 与
 * {@link HookRegistry}（均作为 BeanPostProcessor 在启动时自动捕获 @Tool/@Hook 方法）。
 * 面向接口编程：调用方仅依赖本接口，不依赖具体实现。
 */
public interface IocContainer {

    /**
     * 注册一批 BeanDefinition（手动注册方式）。
     *
     * @param definitions 待注册的 Bean 定义；不允许包含 null
     */
    void register(BeanDefinition... definitions);

    /**
     * 扫描指定包（含子包）下所有 @Component 类并注册为 BeanDefinition。
     *
     * @param basePackage 待扫描的根包路径，如 com.learn.mycc
     */
    void register(String basePackage);

    /**
     * 直接按类型注册为 Bean（快捷方式，等价于逐个 BeanDefinition.from）。
     *
     * @param types 待注册的组件类型；不允许为 null
     */
    void register(Class<?>... types);

    /**
     * 注册 bean 后置处理器，将对之后创建的每个 bean 生效。
     *
     * @param processor 后置处理器实例
     */
    void addBeanPostProcessor(BeanPostProcessor processor);

    /** 启动容器：预创建全部已注册的单例（依赖会在期间递归创建）。 */
    void start();

    /**
     * 按类型取单例 Bean，未创建则即时创建。
     *
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 单例实例
     */
    <T> T getBean(Class<T> type);

    /**
     * 按运行时类型收集所有已创建的单例。
     *
     * @param type 目标接口/超类型
     * @param <T>  目标类型
     * @return 匹配类型的单例列表，可能为空
     */
    <T> List<T> getBeansOfType(Class<T> type);

    /** 容器内置的工具注册表（自动捕获所有 @Tool 方法，按注册顺序）。 */
    ToolRegistry getToolRegistry();

    /** 容器内置的钩子注册表（自动捕获所有 @Hook 方法，按事件类型分组）。 */
    HookRegistry getHookRegistry();

    /** 关闭容器：按创建逆序销毁 DisposableBean 并清空缓存。 */
    void close();

    /**
     * 工厂方法：创建默认 IoC 容器实现。
     *
     * @return 新的 {@link DefaultIocContainer}
     */
    static IocContainer create() {
        return new DefaultIocContainer();
    }
}

final class DefaultIocContainer implements IocContainer {

    /** 底层 Bean 工厂，承担全部实例化、注入与生命周期逻辑。 */
    private final BeanFactory beanFactory = new BeanFactory();

    /** 类路径扫描器，用于 register(String basePackage) 时的 @Component 发现。 */
    private final AnnotationScanner scanner = new AnnotationScanner(Thread.currentThread().getContextClassLoader());

    /** 工具注册表，注册为 BeanPostProcessor 以在 bean 创建后自动捕获 @Tool。 */
    private final ToolRegistry toolRegistry = new ToolRegistry();

    /** 钩子注册表，注册为 BeanPostProcessor 以在 bean 创建后自动捕获 @Hook。 */
    private final HookRegistry hookRegistry = new HookRegistry();

    DefaultIocContainer() {
        // 将工具/钩子注册表作为后置处理器挂入工厂：每个 bean 创建完成即可被扫描
        beanFactory.addBeanPostProcessor(toolRegistry);
        beanFactory.addBeanPostProcessor(hookRegistry);
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
    public void register(Class<?>... types) {
        BeanDefinition[] definitions = Arrays.stream(types).map(BeanDefinition::from).toArray(BeanDefinition[]::new);
        beanFactory.register(definitions);
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
    public HookRegistry getHookRegistry() {
        return hookRegistry;
    }

    @Override
    public void close() {
        beanFactory.close();
    }
}
