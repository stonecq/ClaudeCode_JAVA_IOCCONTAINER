package com.learn.mycc.core.bean;

import com.learn.mycc.core.exception.MyccException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单例 Bean 工厂：IoC 容器的实例化内核，管理 BeanDefinition 注册、单例缓存与生命周期。
 * 设计要点：
 * ① 按类型（Class）注册与按类型取 Bean，不支持按名称取（当前口径，YAGNI）；
 * ② 注入策略：构造器注入优先、字段注入兜底；
 * ③ 循环依赖检测：用“创建中集合 creating”在实例化入口注册、finally 中移除，
 *    同一类型在未完成创建前再次进入即判定为循环依赖并抛异常；
 * ④ 每创建一个 bean 都会按注册顺序应用 BeanPostProcessor（供 ToolRegistry 收集 @Tool）；
 * ⑤ 记录创建顺序 creationOrder，close 时逆序销毁，保证依赖方先于被依赖方释放。
 * 线程安全性：本类假设在单线程启动/使用场景下运行，未做并发同步。
 */
public class BeanFactory {

    /** 类型 → BeanDefinition 注册表；LinkedHashMap 保持注册顺序，供 preInstantiate 按序创建。 */
    private final Map<Class<?>, BeanDefinition> definitionsByType = new LinkedHashMap<>();

    /** 类型 → 已创建完成的单例缓存；HashMap 提供 O(1) 查找。 */
    private final Map<Class<?>, Object> singletons = new HashMap<>();

    /** “正在创建中”的类型集合（标识语义，无实际对象放进 map 值）；
     *  采用 IdentityHashMap 以对象身份而非 equals 比较，杜绝类型重写 equals 的干扰。 */
    private final Set<Class<?>> creating = Collections.newSetFromMap(new IdentityHashMap<>());

    /** 已注册的 BeanPostProcessor 列表，按注册顺序依次应用；允许为空。 */
    private final List<BeanPostProcessor> postProcessors = new ArrayList<>();

    /** 单例创建完成顺序；close 时据此逆序销毁实现“后创建先销毁”。 */
    private final List<Class<?>> creationOrder = new ArrayList<>();

    /**
     * 批量注册 BeanDefinition。
     *
     * @param definitions 待注册的定义，可变参数；若同一类型重复注册抛出异常
     * @throws MyccException 类型已被注册时抛出（putIfAbsent 返回非 null 即重复）
     */
    public void register(BeanDefinition... definitions) {
        for (BeanDefinition definition : definitions) {
            Class<?> type = definition.getType();
            // putIfAbsent 原子性地避免覆盖，返回非 null 说明该 key 已存在
            if (definitionsByType.putIfAbsent(type, definition) != null) {
                throw new MyccException("重复注册 Bean 类型: " + type.getName());
            }
        }
    }

    /**
     * 注册一个 BeanPostProcessor，将对之后创建的每个 bean 生效
     * （对标 Spring 的 bean 后置处理器）。
     *
     * @param processor 后置处理器实例，不允许为 null
     */
    public void addBeanPostProcessor(BeanPostProcessor processor) {
        postProcessors.add(processor);
    }

    /**
     * @return 已注册后置处理器的不可变快照，保持注册顺序
     */
    public List<BeanPostProcessor> getBeanPostProcessors() {
        return List.copyOf(postProcessors);
    }

    /**
     * 按类型取 Bean：优先命中单例缓存；未创建则按注册定义即时创建。
     * 返回的始终是容器内的单例对象（每次调用同一类型返回同一实例）。
     *
     * @param type 目标类型，不允许为 null
     * @return 该类型的单例；必要时即时创建
     * @throws MyccException 类型未注册、存在循环依赖或实例化失败时抛出
     */
    public <T> T getBean(Class<T> type) {
        Object bean = singletons.get(type);
        if (bean != null) {
            return type.cast(bean);
        }
        BeanDefinition definition = definitionsByType.get(type);
        if (definition == null) {
            throw new MyccException("未注册 Bean 类型: " + type.getName());
        }
        return type.cast(createBean(definition));
    }

    /**
     * 创建一个新实例并完成完整的 bean 生命周期：
     * 瞬时化 → 注入 → 初始化回调 → 后置处理 → 入缓存。
     *
     * @param definition bean 元数据
     * @return 创建完成的实例
     * @throws MyccException 循环依赖或实例化失败时抛出
     */
    private Object createBean(BeanDefinition definition) {
        Class<?> type = definition.getType();
        // creating.add 返回 false 说明该类型已在创建中 → 存在循环依赖，立即终止
        if (!creating.add(type)) {
            throw new MyccException("检测到循环依赖，涉及类型: " + type.getName());
        }
        try {
            Object instance = instantiate(definition);
            invokeInit(instance);
            // 后置处理器可按序改造实例（如 ToolRegistry 反射扫描 @Tool 并注册元数据）
            for (BeanPostProcessor processor : postProcessors) {
                instance = processor.postProcessAfterInitialization(instance, definition.getName());
            }
            singletons.put(type, instance);
            creationOrder.add(type);
            return instance;
        } finally {
            // 无论成败都要移出创建中集合，否则一次失败会让该类型永久被误判为循环依赖
            creating.remove(type);
        }
    }

    /** @param instance 实例；若实现 InitializingBean 则在装配完成后执行 afterPropertiesSet */
    private void invokeInit(Object instance) {
        if (instance instanceof InitializingBean bean) {
            bean.afterPropertiesSet();
        }
    }

    /**
     * 按运行时类型收集所有已创建的单例。
     *
     * @param type 目标接口/超类型，不允许为 null
     * @return 匹配类型的单例列表；与注册顺序无关（取自 HashMap 遍历），可能为空
     */
    public <T> List<T> getBeansOfType(Class<T> type) {
        return singletons.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /**
     * 容器启动：按注册顺序实例化全部单例。
     * 依赖会被 getBean 递归触发创建，因此实际创建顺序为“被依赖方先、依赖方后”，
     * 这也是 close 反向销毁顺序正确的根本保证。
     */
    public void preInstantiateSingletons() {
        for (Class<?> type : definitionsByType.keySet()) {
            getBean(type);
        }
    }

    /**
     * 关闭容器：按创建顺序逆序调用 DisposableBean.destroy()（后创建先销毁），
     * 随后清空单例缓存与创建顺序记录。
     * 幂等：清空后可安全重复调用。
     */
    public void close() {
        for (int i = creationOrder.size() - 1; i >= 0; i--) {
            Object bean = singletons.get(creationOrder.get(i));
            if (bean instanceof DisposableBean disposable) {
                disposable.destroy();
            }
        }
        singletons.clear();
        creationOrder.clear();
    }

    /**
     * 依据注入方式实例化：有注入构造器走构造器注入（先解析构造参数依赖），
     * 否则走无参构造 + 字段注入。
     *
     * @param definition bean 元数据
     * @return 已注入但尚未执行初始化回调的实例
     * @throws MyccException 依赖解析或反射实例化失败时抛出
     */
    private Object instantiate(BeanDefinition definition) {
        Constructor<?> constructor = definition.getInjectionConstructor();
        if (constructor != null) {
            Object[] args = resolveDependencies(constructor.getParameterTypes());
            return newInstance(constructor, args);
        }
        Object instance = newInstance(noArgConstructor(definition.getType()));
        injectFields(definition, instance);
        return instance;
    }

    /**
     * 依次解析构造参数的依赖实例（按参数位置一一对应）。
     *
     * @param types 构造器形参类型数组
     * @return 与 types 等长、按序填充的实例数组
     * @throws MyccException 任一依赖类型未注册或存在循环依赖时抛出
     */
    private Object[] resolveDependencies(Class<?>[] types) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = getBean(types[i]);
        }
        return args;
    }

    /**
     * 反射为实例的 @Inject 字段注入依赖（针对字段注入路径）。
     * 依赖通过 getBean 递归解析，可能触发嵌套创建与循环依赖检测。
     *
     * @param definition 提供注入字段清单
     * @param instance   目标实例
     * @throws MyccException 字段反射写入失败时抛出
     */
    private void injectFields(BeanDefinition definition, Object instance) {
        for (Field field : definition.getInjectFields()) {
            Object dependency = getBean(field.getType());
            try {
                // 私有字段在 JDK 17 + 模块化下需显式开放可见性后才能写入
                field.setAccessible(true);
                field.set(instance, dependency);
            } catch (IllegalAccessException e) {
                throw new MyccException("字段注入失败: " + field, e);
            }
        }
    }

    /**
     * 反射调用构造器创建实例，统一将反射异常包装为框架异常。
     *
     * @param constructor 要调用的构造器
     * @param args        构造参数
     * @return 新实例
     * @throws MyccException 反射创建失败时抛出
     */
    private Object newInstance(Constructor<?> constructor, Object... args) {
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new MyccException("实例化失败: " + constructor.getDeclaringClass().getName(), e);
        }
    }

    /**
     * 获取无参构造器（字段注入路径的前置要求）。
     *
     * @param type 目标类型
     * @return 无参构造器
     * @throws MyccException 类型没有无参构造器（无法字段注入）时抛出
     */
    private Constructor<?> noArgConstructor(Class<?> type) {
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new MyccException("类 " + type.getName() + " 缺少无参构造器（无法字段注入）", e);
        }
    }
}
