package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记配置类：其内部标注 {@link Bean} 的方法在注册时被容器展开为 Bean 定义
 * （对标 Spring 的 @Configuration + @Bean 工厂方法）。以 {@link Component} 为
 * 元注解，使配置类自身也被扫成容器托管的单例；{@code isAnnotationPresent}
 * 不穿越元注解，因此扫描器需显式匹配本注解。
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {
}