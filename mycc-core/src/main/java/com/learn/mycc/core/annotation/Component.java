package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为 IoC 容器托管的组件。
 * 该注解运行期保留（RUNTIME），供 {@link com.learn.mycc.core.scan.AnnotationScanner}
 * 在类路径扫描时通过反射识别；容器将据此为每个组件注册为单例 Bean。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {
}
