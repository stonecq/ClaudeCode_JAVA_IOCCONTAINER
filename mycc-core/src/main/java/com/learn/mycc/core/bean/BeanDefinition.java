package com.learn.mycc.core.bean;

import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.exception.MyccException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bean 元数据：封装一个可被 IoC 容器实例化的类的类型、名称、注入方式。
 * 注入方式与 {@link com.learn.mycc.core.annotation.Inject} 策略一致：
 * 构造器注入优先，字段注入兜底；
 * 二选一（有注入构造器则 injectFields 恒为空，反之亦然）。
 * 由静态工厂 {@link #from(Class)} 从类的反射信息构建，
 * 仅承担元数据描述职责，不做任何实例化。
 */
public final class BeanDefinition {

    /** 组件类型，决定实例化方式与依赖的类型解析；不允许为 null。 */
    private final Class<?> type;

    /** Bean 名称，由类型简单名首字母小写推导（如 UserService → userService）；
     *  不允许为 null。 */
    private final String name;

    /** 注入构造器；null 表示该 bean 走“无参构造 + 字段注入”路径。 */
    private final Constructor<?> injectionConstructor;

    /** 需要注入的字段集合；当使用构造器注入时恒为空（List.of()，不可变）。 */
    private final List<Field> injectFields;

    /** 私有构造：强制通过 {@link #from(Class)} 工厂创建，保证字段不可变且派生规则集中。 */
    private BeanDefinition(Class<?> type, String name,
                           Constructor<?> injectionConstructor, List<Field> injectFields) {
        this.type = type;
        this.name = name;
        this.injectionConstructor = injectionConstructor;
        this.injectFields = injectFields;
    }

    /**
     * 根据组件类型构建 BeanDefinition。
     * 先解析注入构造器：若解析出构造器则注入方式确定为构造器注入（injectFields
     * 为空）；否则回退为字段注入并收集所有 @Inject 字段。
     *
     * @param type 组件类型，不允许为 null
     * @return 对应 BeanDefinition
     * @throws com.learn.mycc.core.exception.MyccException
     *        当存在多个 @Inject 构造器（注入不明确）时抛出
     */
    public static BeanDefinition from(Class<?> type) {
        String name = decapitalize(type.getSimpleName());
        Constructor<?> injectionConstructor = resolveInjectionConstructor(type);
        List<Field> injectFields = injectionConstructor == null ? resolveInjectFields(type) : List.of();
        return new BeanDefinition(type, name, injectionConstructor, injectFields);
    }

    /** @return 组件类型，恒非 null */
    public Class<?> getType() {
        return type;
    }

    /** @return 容器内的 Bean 名称，恒非 null */
    public String getName() {
        return name;
    }

    /** 注入构造器；null 表示使用无参构造 + 字段注入。 */
    public Constructor<?> getInjectionConstructor() {
        return injectionConstructor;
    }

    /** @return 需注入的字段列表，使用构造器注入时为空；返回的是不可变视图 */
    public List<Field> getInjectFields() {
        return injectFields;
    }

    /**
     * 解析注入构造器，规则依次如下：
     * ① 若存在多个标注 @Inject 的构造器，注入不明确，抛异常；
     * ② 恰好一个标注 @Inject 的构造器：使用它；
     * ③ 无 @Inject 但只有一个构造器且带参：唯一构造器即为默认注入入口
     *    （Spring 同款回退）；
     * ④ 其余情况（无参构造 / 多构造器但未标注）：返回 null，退回字段注入。
     *
     * @param type 待解析的组件类型
     * @return 注入构造器；null 表示走字段注入
     * @throws com.learn.mycc.core.exception.MyccException 多个 @Inject 构造器时抛出
     */
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

    /**
     * 收集类型及其全部超类（上溯到但不含 Object）中标注 @Inject 的字段。
     * 上溯超类是关键边界：能注入父类声明的依赖，避免为每个子类重复声明。
     *
     * @param type 待收集的组件类型
     * @return 按“本类在前、父类在后”顺序排列的注入字段列表；可能为空
     */
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

    /**
     * 将类型简单名首字母小写得到 Bean 名称。
     *
     * @param simpleName 类型简单名，不允许为 null
     * @return 首字母小写后的名称；空串原样返回（无字符可小写，安全兜底）
     */
    private static String decapitalize(String simpleName) {
        if (simpleName.isEmpty()) {
            return simpleName;
        }
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
