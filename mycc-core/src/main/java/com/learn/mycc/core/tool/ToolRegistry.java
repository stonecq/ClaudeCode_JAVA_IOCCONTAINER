package com.learn.mycc.core.tool;

import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.bean.BeanPostProcessor;
import com.learn.mycc.core.exception.MyccException;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：实现 {@link BeanPostProcessor}，在 bean 创建完成后反射扫描
 * 其 @Tool 方法并注册。
 * 采用“被动捕获”而非主动扫描：只要把本实例 add 为后置处理器，
 * 任何组件后续创建的 @Tool 都会被自动收集，与 IoC 容器天然解耦。
 * toolsByName 用 LinkedHashMap 保持注册顺序。
 */
public final class ToolRegistry implements BeanPostProcessor {

    /** 工具名 → 工具定义映射；LinkedHashMap 使 getAll 输出与注册顺序一致。 */
    private final Map<String, ToolDefinition> toolsByName = new LinkedHashMap<>();

    /**
     * 容器回调：扫描 bean 的声明方法（不含继承的方法）中带 @Tool 的方法并注册；
     * 未标注的工具方法被忽略。
     *
     * @param bean     创建完成的 bean 实例
     * @param beanName bean 名称（本实现不使用）
     * @return 原 bean 实例（本后置处理器不改造实例）
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool != null) {
                register(new ToolDefinition(tool.name(), tool.description(), bean, method, tool.risk()));
            }
        }
        return bean;
    }

    /**
     * 注册单个工具定义。
     *
     * @param definition 工具定义，不允许为 null
     * @throws com.learn.mycc.core.exception.MyccException 名称为空或与已有工具重名时抛出
     */
    public void register(ToolDefinition definition) {
        String name = definition.getName();
        if (name == null || name.isBlank()) {
            throw new MyccException("工具名称不能为空: " + definition.getMethod());
        }
        // putIfAbsent 原子防重：返回非 null 说明同名工具已存在
        if (toolsByName.putIfAbsent(name, definition) != null) {
            throw new MyccException("重复注册工具: " + name);
        }
    }

    /**
     * @return 已注册工具快照（按注册顺序，不可变）
     */
    public List<ToolDefinition> getAll() {
        return List.copyOf(toolsByName.values());
    }

    /**
     * 按名称取工具。
     *
     * @param name 工具名
     * @return 对应工具定义
     * @throws com.learn.mycc.core.exception.MyccException 工具未注册时抛出
     */
    public ToolDefinition get(String name) {
        ToolDefinition definition = toolsByName.get(name);
        if (definition == null) {
            throw new MyccException("未注册工具: " + name);
        }
        return definition;
    }
}
