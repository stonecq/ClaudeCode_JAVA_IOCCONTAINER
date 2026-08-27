package com.learn.mycc.core.tool;

import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.bean.BeanPostProcessor;
import com.learn.mycc.core.exception.MyccException;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工具注册表：作为 BeanPostProcessor 在 bean 创建后反射捕获 @Tool 方法并注册。 */
public final class ToolRegistry implements BeanPostProcessor {

    private final Map<String, ToolDefinition> toolsByName = new LinkedHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool != null) {
                register(new ToolDefinition(tool.name(), tool.description(), bean, method));
            }
        }
        return bean;
    }

    public void register(ToolDefinition definition) {
        String name = definition.getName();
        if (name == null || name.isBlank()) {
            throw new MyccException("工具名称不能为空: " + definition.getMethod());
        }
        if (toolsByName.putIfAbsent(name, definition) != null) {
            throw new MyccException("重复注册工具: " + name);
        }
    }

    /** 已注册工具快照（按注册顺序）。 */
    public List<ToolDefinition> getAll() {
        return List.copyOf(toolsByName.values());
    }

    public ToolDefinition get(String name) {
        ToolDefinition definition = toolsByName.get(name);
        if (definition == null) {
            throw new MyccException("未注册工具: " + name);
        }
        return definition;
    }
}
