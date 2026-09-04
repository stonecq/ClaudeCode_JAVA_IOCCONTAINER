package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按名称限定 Bean 的注解：
 * <ul>
 *   <li>标注在类型上：覆盖默认的 bean 名（默认类简单名首字母小写）；</li>
 *   <li>标注在构造器形参 / 字段 / 工厂方法参数上：该注入点按名称解析，
 *       规避同一接口多个实现时的二义性。</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER})
public @interface Named {

    /** Bean 名称 */
    String value();
}