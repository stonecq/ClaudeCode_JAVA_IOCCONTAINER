package com.learn.mycc.core.bean;

import com.learn.mycc.core.annotation.Named;
import com.learn.mycc.core.annotation.ScopeType;
import com.learn.mycc.core.exception.MyccException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单例/原型 Bean 工厂：IoC 容器的实例化内核，管理 BeanDefinition 注册、单例缓存与生命周期。
 * 设计要点：
 * ① 按类型（Class）注册/取 Bean；另支持按名称（getBean(String)）与接口可匹配回退；
 * ② 注入策略：构造器注入优先、字段注入兜底；@Named 可在注入点按名称消解多实现二义；
 * ③ 循环依赖检测：用“创建中集合 creating”在实例化入口注册、finally 中移除；
 * ④ 每创建一个 bean 都按注册顺序应用 BeanPostProcessor（供 ToolRegistry 收集 @Tool）；
 * ⑤ 记录创建顺序 creationOrder，close 时逆序销毁，保证依赖方先于被依赖方释放；
 * ⑥ 作用域：SINGLETON 入缓存，PROTOTYPE 每次新建不入缓存不记创建顺序；
 * ⑦ @Bean 工厂方法：先取配置单例再按形参类型解析依赖反射调用，产物走标准生命周期。
 * 线程安全性：本类假设在单线程启动/使用场景下运行，未做并发同步。
 */
public class BeanFactory {

    /** 类型 → BeanDefinition 注册表；LinkedHashMap 保持注册顺序，供 preInstantiate 按序创建。 */
    private final Map<Class<?>, BeanDefinition> definitionsByType = new LinkedHashMap<>();

    /** 名称 → BeanDefinition 查找表；供 getBean(String) 与 @Named 注入消解。 */
    private final Map<String, BeanDefinition> definitionsByName = new HashMap<>();

    /** 类型 → 已创建完成的单例缓存；HashMap 提供 O(1) 查找；含 registerSingleton 外部注入对象。 */
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
            // 名称表同样防覆盖：同名重复注册视为冲突（@Named / @Bean 名重叠）
            if (definitionsByName.putIfAbsent(definition.getName(), definition) != null) {
                throw new MyccException("重复注册 Bean 名称: " + definition.getName());
            }
        }
    }

    /**
     * 注册一个外部注入的单例（如 ToolRegistry/HookRegistry/容器自身）。
     * 该实例直接进单例缓存、按类型可注入；不记创建顺序、不参与生命周期回调。
     *
     * @param type     注册的键类型（通常为实例的真实类型或接口），不允许为 null
     * @param instance 实例，不允许为 null
     * @throws MyccException 类型已有注册定义，或实例为 null 时抛出
     */
    public void registerSingleton(Class<?> type, Object instance) {
        if (instance == null) {
            throw new MyccException("registerSingleton 实例不允许为 null: " + type.getName());
        }
        if (definitionsByType.containsKey(type)) {
            throw new MyccException("重复注册 Bean 类型: " + type.getName());
        }
        singletons.put(type, instance);
    }

    /**
     * 覆盖注册一个外部实例（测试替身 / 运行时替换）：不论目标类型是否已有注册定义，
     * 都写入单例缓存；getBean 优先返回本缓存，从而遮蔽定义的实例化。
     * 与 {@link #registerSingleton} 的严格冲突契约区分——该能力专供测试等注入替身场景
     * （等价 Spring 的 @MockBean / 显式 bean 覆盖）。
     *
     * @param type     被覆盖的键类型，不允许为 null
     * @param instance 替身实例，不允许为 null
     * @throws MyccException 实例为 null 时抛出
     */
    public void overrideSingleton(Class<?> type, Object instance) {
        if (instance == null) {
            throw new MyccException("overrideSingleton 实例不允许为 null: " + type.getName());
        }
        singletons.put(type, instance);
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
     * 按类型取 Bean：精确命中单例缓存 → 注册定义即时创建 → 接口可匹配回退
     * （默认实现唯一实例，多实现抛“多个可匹配”，无实现抛“未注册”）。
     *
     * @param type 目标类型，不允许为 null
     * @return 该类型的单例；必要时即时创建
     * @throws MyccException 类型未注册、存在多个可匹配实现、循环依赖或实例化失败时抛出
     */
    public <T> T getBean(Class<T> type) {
        Object bean = singletons.get(type);
        if (bean != null) {
            return type.cast(bean);
        }
        BeanDefinition definition = definitionsByType.get(type);
        if (definition != null) {
            return type.cast(createBean(definition));
        }
        return type.cast(resolveAssignable(type));
    }

    /**
     * 带构造参数覆盖取 Bean：prototype 绑定缝的关键入口。
     * 单例已存在则忽略 args 返回现有；prototype 则将 args 按位置覆盖构造/工厂形参，
     * 未命中处按类型/按名称从容器解析（如 AgentLoop 绑定已加载 Session、ReplLoop 全参覆盖）。
     *
     * @param type 目标类型，不允许为 null
     * @param args 按位置优先的构造/工厂参数覆盖；单例场景被忽略
     * @return 实例（prototype 每次新建，单例为缓存中的现有实例）
     * @throws MyccException 类型未注册或依赖无法解析时抛出
     */
    public <T> T getBean(Class<T> type, Object... args) {
        Object bean = singletons.get(type);
        if (bean != null) {
            return type.cast(bean);
        }
        BeanDefinition definition = definitionsByType.get(type);
        if (definition == null) {
            throw new MyccException("未注册 Bean 类型: " + type.getName());
        }
        if (definition.getScope() == ScopeType.PROTOTYPE) {
            return type.cast(createBean(definition, args));
        }
        // 单例尚未创建：忽略 args，走常规解析创建
        return type.cast(createBean(definition));
    }

    /**
     * 按名称取 Bean：命中注册定义后按其类型解析（含即时创建）。
     * 名称来源：类简单名首字母小写 / @Named 类级覆盖 / @Bean.name 或方法名。
     *
     * @param name Bean 名，不允许为 null
     * @return 对应实例
     * @throws MyccException 该名称未注册时抛出
     */
    public Object getBean(String name) {
        BeanDefinition definition = definitionsByName.get(name);
        if (definition == null) {
            throw new MyccException("未注册 Bean 名称: " + name);
        }
        return getBean(definition.getType());
    }

    /**
     * 创建一个新实例并完成完整的 bean 生命周期：
     * 瞬时化 → 注入 → 初始化回调 → 后置处理 → 入缓存（prototype 跳过）。
     *
     * @param definition bean 元数据
     * @return 创建完成的实例
     * @throws MyccException 循环依赖或实例化失败时抛出
     */
    private Object createBean(BeanDefinition definition) {
        return createBean(definition, new Object[0]);
    }

    /**
     * 带参数覆盖创建：见 {@link #createBean(BeanDefinition)}，overrides 透传给 instantiate。
     */
    private Object createBean(BeanDefinition definition, Object[] overrides) {
        Class<?> type = definition.getType();
        // creating.add 返回 false 说明该类型已在创建中 → 存在循环依赖，立即终止
        if (!creating.add(type)) {
            throw new MyccException("检测到循环依赖，涉及类型: " + type.getName());
        }
        try {
            Object instance = instantiate(definition, overrides);
            invokeInit(instance);
            // 后置处理器可按序改造实例（如 ToolRegistry 反射扫描 @Tool 并注册元数据）
            for (BeanPostProcessor processor : postProcessors) {
                instance = processor.postProcessAfterInitialization(instance, definition.getName());
            }
            if (definition.getScope() == ScopeType.PROTOTYPE) {
                // 原型不入缓存、不记创建顺序：每次 getBean 新建，仍执行 init + BPP
                return instance;
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
     * 容器启动：按注册顺序实例化全部单例定义；prototype 定义跳过（按需创建，杜绝
     * 无参调用失败）。依赖会被 getBean 递归触发创建，因此实际创建顺序为
     * “被依赖方先、依赖方后”，这也是 close 反向销毁顺序正确的根本保证。
     */
    public void preInstantiateSingletons() {
        for (Class<?> type : definitionsByType.keySet()) {
            if (definitionsByType.get(type).getScope() == ScopeType.PROTOTYPE) {
                continue;
            }
            getBean(type);
        }
    }

    /**
     * 关闭容器：按创建顺序逆序销毁单例（后创建先销毁）——先执行 DisposableBean.destroy()，
     * @Bean 产物再追加 destroyMethod() 反射调用；随后清空单例缓存与创建顺序记录。
     * 幂等：清空后可安全重复调用。
     */
    public void close() {
        for (int i = creationOrder.size() - 1; i >= 0; i--) {
            Class<?> type = creationOrder.get(i);
            Object bean = singletons.get(type);
            if (bean instanceof DisposableBean disposable) {
                disposable.destroy();
            }
            BeanDefinition definition = definitionsByType.get(type);
            if (definition != null && definition.isFactoryMethod()
                    && !definition.getDestroyMethod().isBlank()) {
                invokeDestroyMethod(bean, definition);
            }
        }
        singletons.clear();
        creationOrder.clear();
    }

    /**
     * 接口可匹配回退：单例按 isInstance、定义按 isAssignableFrom 收集可匹配候选，
     * 定义侧惰性实例化；身份去重后恰好一个返回，多个抛“多个可匹配”，0 个抛“未注册”。
     *
     * @param requestedType 请求的接口/超类型
     * @return 匹配到的唯一实例
     * @throws MyccException 匹配到多个、0 个，或实例化失败时抛出
     */
    private Object resolveAssignable(Class<?> requestedType) {
        List<Object> candidates = new ArrayList<>();
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        for (Object singleton : singletons.values()) {
            if (requestedType.isInstance(singleton) && seen.put(singleton, Boolean.TRUE) == null) {
                candidates.add(singleton);
            }
        }
        for (BeanDefinition definition : definitionsByType.values()) {
            if (!requestedType.isAssignableFrom(definition.getType())) {
                continue;
            }
            // 正在创建中的类型跳过，避免把自身误判为候选重入触发循环依赖
            if (creating.contains(definition.getType())) {
                continue;
            }
            // 经 getBean 复用已创建的单例（否则接口回退会重复实例化同类型，误判"多个可匹配"）
            Object instance = getBean(definition.getType());
            if (seen.put(instance, Boolean.TRUE) == null) {
                candidates.add(instance);
            }
        }
        if (candidates.size() > 1) {
            List<String> names = candidates.stream().map(c -> c.getClass().getName()).toList();
            throw new MyccException("请求类型 " + requestedType.getName() + " 存在多个可匹配的 Bean: " + names);
        }
        if (candidates.isEmpty()) {
            throw new MyccException("未注册 Bean 类型: " + requestedType.getName());
        }
        return candidates.get(0);
    }

    /**
     * 依据注入方式实例化：工厂方法 → 取配置单例 + 反射调用；有注入构造器 → 构造器注入；
     * 否则无参构造 + 字段注入。
     *
     * @param definition bean 元数据
     * @param overrides  构造/工厂形参按位置覆盖的可选参数（prototype 绑定缝），可为空
     * @return 已注入但尚未执行初始化回调的实例
     * @throws MyccException 依赖解析或反射实例化失败时抛出
     */
    private Object instantiate(BeanDefinition definition, Object[] overrides) {
        if (definition.isFactoryMethod()) {
            return instantiateFromFactory(definition, overrides);
        }
        Constructor<?> constructor = definition.getInjectionConstructor();
        if (constructor != null) {
            Object[] args = resolveDependencies(constructor.getParameters(), overrides);
            return newInstance(constructor, args);
        }
        Object instance = newInstance(noArgConstructor(definition.getType()));
        injectFields(definition, instance);
        return instance;
    }

    /**
     * 工厂模式实例化：先按类型取配置类单例（getBean 保证其已创建），再按方法形参
     * 解析依赖并反射调用 @Bean 方法，产物走调用方标准生命周期。
     *
     * @param definition 工厂式 BeanDefinition
     * @param overrides  按位置覆盖方法形参的可选参数，可为空
     * @return 工厂方法产出的实例
     * @throws MyccException 依赖解析或反射调用失败时抛出
     */
    private Object instantiateFromFactory(BeanDefinition definition, Object[] overrides) {
        Object config = getBean(definition.getOwnerConfigType());
        Method method = definition.getFactoryMethod();
        Object[] args = resolveDependencies(method.getParameters(), overrides);
        try {
            method.setAccessible(true);
            return method.invoke(config, args);
        } catch (ReflectiveOperationException e) {
            throw new MyccException("工厂方法调用失败: " + method, e);
        }
    }

    /**
     * 按形参与可选覆盖参数解析依赖，三段式：
     * ① 按位置优先：overrides[i] 类型匹配 parameters[i] 则直接采用；
     * ② 类型匹配：未覆盖处从剩余 overrides 中找类型匹配的首个值；
     * ③ 容器解析：带 @Named 按名称，否则按类型 {@link #getBean(Class)}。
     *
     * @param parameters 构造器 / 工厂方法形参列表
     * @param overrides  按位置优先的覆盖参数，可为空
     * @return 与 parameters 等长、按序填充的实例数组
     * @throws MyccException 任一依赖类型未注册或存在循环依赖时抛出
     */
    private Object[] resolveDependencies(Parameter[] parameters, Object[] overrides) {
        Object[] args = new Object[parameters.length];
        boolean[] resolved = new boolean[parameters.length];
        // ① 按位置优先覆盖
        for (int i = 0; i < parameters.length && i < overrides.length; i++) {
            if (overrides[i] != null && parameters[i].getType().isInstance(overrides[i])) {
                args[i] = overrides[i];
                resolved[i] = true;
            }
        }
        // ② 剩余覆盖参数按类型匹配
        for (int i = 0; i < parameters.length; i++) {
            if (resolved[i]) {
                continue;
            }
            for (Object candidate : overrides) {
                if (candidate != null && parameters[i].getType().isInstance(candidate)) {
                    args[i] = candidate;
                    resolved[i] = true;
                    break;
                }
            }
        }
        // ③ 容器解析：@Named 按名称，否则按类型
        for (int i = 0; i < parameters.length; i++) {
            if (resolved[i]) {
                continue;
            }
            Named named = parameters[i].getAnnotation(Named.class);
            if (named != null) {
                args[i] = getBean(named.value());
            } else {
                args[i] = getBean(parameters[i].getType());
            }
        }
        return args;
    }

    /**
     * 反射为实例的 @Inject 字段注入依赖（针对字段注入路径）。
     * 依赖通过 getBean 递归解析，可能触发嵌套创建与循环依赖检测；
     * 带 @Named 的字段按名称取，规避接口多实现二义。
     *
     * @param definition 提供注入字段清单
     * @param instance   目标实例
     * @throws MyccException 字段反射写入失败时抛出
     */
    private void injectFields(BeanDefinition definition, Object instance) {
        for (Field field : definition.getInjectFields()) {
            Object dependency = resolveFieldDependency(field);
            try {
                // 私有字段在 JDK 17 + 模块化下需显式开放可见性后才能写入
                field.setAccessible(true);
                field.set(instance, dependency);
            } catch (IllegalAccessException e) {
                throw new MyccException("字段注入失败: " + field, e);
            }
        }
    }

    /** @return 字段依赖：带 @Named 按名称取，否则按字段类型取 */
    private Object resolveFieldDependency(Field field) {
        Named named = field.getAnnotation(Named.class);
        if (named != null) {
            return getBean(named.value());
        }
        return getBean(field.getType());
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

    /**
     * 容器关闭时反射调用 @Bean 标注的 destroyMethod（如 Terminal.close）。
     *
     * @param bean       工厂产物实例
     * @param definition 工厂式 BeanDefinition（提供 destroyMethod 名）
     * @throws MyccException 方法不存在或反射调用失败时抛出
     */
    private void invokeDestroyMethod(Object bean, BeanDefinition definition) {
        try {
            Method method = bean.getClass().getMethod(definition.getDestroyMethod());
            method.setAccessible(true);
            method.invoke(bean);
        } catch (ReflectiveOperationException e) {
            throw new MyccException("销毁方法调用失败: " + definition.getDestroyMethod()
                    + " on " + bean.getClass().getName(), e);
        }
    }
}