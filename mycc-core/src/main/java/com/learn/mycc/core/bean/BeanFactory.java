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
 * 单例 Bean 工厂：按类型注册 BeanDefinition，构造器注入优先、字段注入兜底，检测循环依赖。
 * 每创建一个 bean 都会调用已注册的 {@link BeanPostProcessor}。
 */
public class BeanFactory {

    private final Map<Class<?>, BeanDefinition> definitionsByType = new LinkedHashMap<>();
    private final Map<Class<?>, Object> singletons = new HashMap<>();
    private final Set<Class<?>> creating = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<BeanPostProcessor> postProcessors = new ArrayList<>();
    private final List<Class<?>> creationOrder = new ArrayList<>();

    public void register(BeanDefinition... definitions) {
        for (BeanDefinition definition : definitions) {
            Class<?> type = definition.getType();
            if (definitionsByType.putIfAbsent(type, definition) != null) {
                throw new MyccException("重复注册 Bean 类型: " + type.getName());
            }
        }
    }

    public void addBeanPostProcessor(BeanPostProcessor processor) {
        postProcessors.add(processor);
    }

    public List<BeanPostProcessor> getBeanPostProcessors() {
        return List.copyOf(postProcessors);
    }

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

    private Object createBean(BeanDefinition definition) {
        Class<?> type = definition.getType();
        if (!creating.add(type)) {
            throw new MyccException("检测到循环依赖，涉及类型: " + type.getName());
        }
        try {
            Object instance = instantiate(definition);
            invokeInit(instance);
            for (BeanPostProcessor processor : postProcessors) {
                instance = processor.postProcessAfterInitialization(instance, definition.getName());
            }
            singletons.put(type, instance);
            creationOrder.add(type);
            return instance;
        } finally {
            creating.remove(type);
        }
    }

    private void invokeInit(Object instance) {
        if (instance instanceof InitializingBean bean) {
            bean.afterPropertiesSet();
        }
    }

    public <T> List<T> getBeansOfType(Class<T> type) {
        return singletons.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /** 容器启动：按注册顺序实例化全部单例（含依赖，依赖先于依赖方创建）。 */
    public void preInstantiateSingletons() {
        for (Class<?> type : definitionsByType.keySet()) {
            getBean(type);
        }
    }

    /** 按创建顺序逆序销毁 DisposableBean，并清空单例缓存。 */
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

    private Object[] resolveDependencies(Class<?>[] types) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = getBean(types[i]);
        }
        return args;
    }

    private void injectFields(BeanDefinition definition, Object instance) {
        for (Field field : definition.getInjectFields()) {
            Object dependency = getBean(field.getType());
            try {
                field.setAccessible(true);
                field.set(instance, dependency);
            } catch (IllegalAccessException e) {
                throw new MyccException("字段注入失败: " + field, e);
            }
        }
    }

    private Object newInstance(Constructor<?> constructor, Object... args) {
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new MyccException("实例化失败: " + constructor.getDeclaringClass().getName(), e);
        }
    }

    private Constructor<?> noArgConstructor(Class<?> type) {
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new MyccException("类 " + type.getName() + " 缺少无参构造器（无法字段注入）", e);
        }
    }
}
