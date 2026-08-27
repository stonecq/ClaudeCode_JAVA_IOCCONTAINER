package com.learn.mycc.core.bean;

import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.exception.MyccException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bean 元数据：类型、名称、注入方式（构造器注入优先，字段注入兜底）。 */
public final class BeanDefinition {

    private final Class<?> type;
    private final String name;
    private final Constructor<?> injectionConstructor;
    private final List<Field> injectFields;

    private BeanDefinition(Class<?> type, String name,
                           Constructor<?> injectionConstructor, List<Field> injectFields) {
        this.type = type;
        this.name = name;
        this.injectionConstructor = injectionConstructor;
        this.injectFields = injectFields;
    }

    public static BeanDefinition from(Class<?> type) {
        String name = decapitalize(type.getSimpleName());
        Constructor<?> injectionConstructor = resolveInjectionConstructor(type);
        List<Field> injectFields = injectionConstructor == null ? resolveInjectFields(type) : List.of();
        return new BeanDefinition(type, name, injectionConstructor, injectFields);
    }

    public Class<?> getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    /** 注入构造器；null 表示使用无参构造 + 字段注入。 */
    public Constructor<?> getInjectionConstructor() {
        return injectionConstructor;
    }

    public List<Field> getInjectFields() {
        return injectFields;
    }

    private static Constructor<?> resolveInjectionConstructor(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        List<Constructor<?>> annotated = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .toList();
        if (annotated.size() > 1) {
            throw new MyccException("类 " + type.getName() + " 存在多个 @Inject 构造器，不明确");
        }
        if (annotated.size() == 1) {
            return annotated.get(0);
        }
        if (constructors.length == 1 && constructors[0].getParameterCount() > 0) {
            return constructors[0];
        }
        // 其余情况（无参构造 / 多构造器未指定）：退回字段注入
        return null;
    }

    private static List<Field> resolveInjectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static String decapitalize(String simpleName) {
        if (simpleName.isEmpty()) {
            return simpleName;
        }
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
