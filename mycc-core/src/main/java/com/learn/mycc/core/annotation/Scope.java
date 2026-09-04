package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Bean 作用域：标注在类或 {@link Bean} 工厂方法上，覆盖默认的
 * {@link ScopeType#SINGLETON}。类级标注对当前类生效；方法级标注只对
 * 该 {@code @Bean} 产出的定义生效，优先级高于类级。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Scope {

    ScopeType value() default ScopeType.SINGLETON;
}