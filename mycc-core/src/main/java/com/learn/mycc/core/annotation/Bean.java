package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 {@link Configuration} 类中的工厂方法：其返回类型作为 Bean 的注册键，
 * 方法名（或 {@code name()}）作为 Bean 名称；形参按类型/名称由容器解析注入。
 * 产物走标准生命周期，并在容器关闭时反射调用 {@code destroyMethod()}（如
 * JLine Terminal 的 close），仅当 non-blank 时触发。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Bean {

    /** Bean 名称；留空时默认取方法名。 */
    String name() default "";

    /** 容器关闭时反射调用的销毁方法名；留空表示无销毁回调。 */
    String destroyMethod() default "";
}