package com.learn.mycc.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 依赖注入标记，声明一个类成员为需要容器注入的依赖，运行期保留以便反射读取。
 * 注入策略：构造器注入优先，字段注入兜底——
 * ① 同一类中出现多个标注 @Inject 的构造器会被视为不明确而抛异常；
 * ② 存在唯一 @Inject 构造器时使用它；
 * ③ 否则若该类仅有一个带参构造器也直接使用；
 * ④ 其余情况退回无参构造 + 逐字段 @Inject 注入。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Inject {
}
