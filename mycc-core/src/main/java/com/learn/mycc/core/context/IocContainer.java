package com.learn.mycc.core.context;

import com.learn.mycc.core.annotation.Bean;
import com.learn.mycc.core.annotation.Configuration;
import com.learn.mycc.core.annotation.Scope;
import com.learn.mycc.core.annotation.ScopeType;
import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import com.learn.mycc.core.bean.BeanPostProcessor;
import com.learn.mycc.core.hook.HookRegistry;
import com.learn.mycc.core.scan.AnnotationScanner;
import com.learn.mycc.core.tool.ToolRegistry;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * IoC 容器门面：对 {@link BeanFactory} 的简化包装，对外提供统一的
 * 注册 → 启动 → 取 Bean → 关闭 生命周期入口。
 * 具体实现 {@link DefaultIocContainer}（同文件、包私有）委托 {@link BeanFactory}，
 * 并额外持有 {@link AnnotationScanner}（包扫描注册）、{@link ToolRegistry} 与
 * {@link HookRegistry}（均作为 BeanPostProcessor 在启动时自动捕获 @Tool/@Hook 方法）。
 * 注册时会将 {@link Configuration} 类的 {@link Bean} 工厂方法展开为独立 BeanDefinition。
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
     * 注册一个外部注入的单例（如 ToolRegistry/HookRegistry/容器自身）。
     * 该实例直接进单例缓存、按类型可注入；不参与生命周期回调。
     *
     * @param type     注册的键类型
     * @param instance 实例，不允许为 null
     */
    void registerSingleton(Class<?> type, Object instance);

    /**
     * 注册 bean 后置处理器，将对之后创建的每个 bean 生效。
     *
     * @param processor 后置处理器实例
     */
    void addBeanPostProcessor(BeanPostProcessor processor);

    /** 启动容器：预创建全部已注册的单例（依赖会在期间递归创建；prototype 按需创建）。 */
    void start();

    /**
     * 按类型取单例 Bean，未创建则即时创建；未精确命中时尝试接口可匹配回退。
     *
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 单例实例
     */
    <T> T getBean(Class<T> type);

    /**
     * 带构造参数覆盖取 Bean：prototype 绑定缝。
     * 单例已存在则忽略 args 返回现有；prototype 将 args 按位置覆盖构造/工厂形参。
     *
     * @param type 目标类型
     * @param args 按位置优先的构造/工厂参数覆盖
     * @param <T>  目标类型
     * @return 实例（prototype 每次新建）
     */
    <T> T getBean(Class<T> type, Object... args);

    /**
     * 按名称取 Bean：命中注册定义后按其类型解析（含即时创建）。
     *
     * @param name Bean 名（类简单名首字母小写 / @Named 覆盖 / @Bean.name）
     * @return 对应实例
     */
    Object getBean(String name);

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

    /** 关闭容器：按创建逆序销毁 DisposableBean（含 @Bean destroyMethod）并清空缓存。 */
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
        // 内置注册表与容器自身作为单例注入：按类型可注入（CliContext 等经容器做 prototype 绑定）
        beanFactory.registerSingleton(ToolRegistry.class, toolRegistry);
        beanFactory.registerSingleton(HookRegistry.class, hookRegistry);
        beanFactory.registerSingleton(IocContainer.class, this);
    }

    @Override
    public void register(BeanDefinition... definitions) {
        registerAll(definitions);
    }

    @Override
    public void register(String basePackage) {
        registerAll(scanner.scanBeanDefinitions(basePackage).toArray(BeanDefinition[]::new));
    }

    @Override
    public void register(Class<?>... types) {
        BeanDefinition[] definitions = Arrays.stream(types).map(BeanDefinition::from).toArray(BeanDefinition[]::new);
        registerAll(definitions);
    }

    @Override
    public void registerSingleton(Class<?> type, Object instance) {
        beanFactory.registerSingleton(type, instance);
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
    public <T> T getBean(Class<T> type, Object... args) {
        return beanFactory.getBean(type, args);
    }

    @Override
    public Object getBean(String name) {
        return beanFactory.getBean(name);
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

    /**
     * 注册的公共入口：先展开 @Configuration 类的 @Bean 工厂方法为独立 BeanDefinition，
     * 再统一交 BeanFactory 注册；三类 register 重载都经此收敛，保证展开逻辑唯一。
     */
    private void registerAll(BeanDefinition... definitions) {
        beanFactory.register(expandFactoryMethods(definitions));
    }

    /**
     * 遍历待注册定义，凡类型标注 {@link Configuration} 的，将其每个 {@link Bean}
     * 方法展开为工厂式 BeanDefinition（名称取 @Bean.name 或方法名，作用域取 @Scope 或单例）。
     */
    private BeanDefinition[] expandFactoryMethods(BeanDefinition... definitions) {
        List<BeanDefinition> all = new ArrayList<>();
        for (BeanDefinition definition : definitions) {
            all.add(definition);
            Class<?> configType = definition.getType();
            if (!configType.isAnnotationPresent(Configuration.class)) {
                continue;
            }
            for (Method method : configType.getDeclaredMethods()) {
                Bean bean = method.getAnnotation(Bean.class);
                if (bean == null) {
                    continue;
                }
                String name = bean.name().isBlank() ? method.getName() : bean.name();
                Scope scope = method.getAnnotation(Scope.class);
                ScopeType scopeType = scope != null ? scope.value() : ScopeType.SINGLETON;
                all.add(BeanDefinition.fromFactoryMethod(configType, method, name, scopeType));
            }
        }
        return all.toArray(BeanDefinition[]::new);
    }
}