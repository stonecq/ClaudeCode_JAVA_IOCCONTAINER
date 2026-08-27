package com.learn.mycc.core.tool;

import java.lang.reflect.Method;

/** 工具定义：@Tool 方法的元数据快照（名称、描述、所属 bean 与方法）。 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final Object bean;
    private final Method method;

    public ToolDefinition(String name, String description, Object bean, Method method) {
        this.name = name;
        this.description = description;
        this.bean = bean;
        this.method = method;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Object getBean() {
        return bean;
    }

    public Method getMethod() {
        return method;
    }
}
