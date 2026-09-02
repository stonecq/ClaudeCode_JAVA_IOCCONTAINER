package com.learn.mycc.core.tool;

import java.lang.reflect.Method;

/**
 * 工具定义：@Tool 方法的元数据快照（名称、描述、所属 bean 与方法引用）。
 * 由 {@link ToolRegistry} 在 bean 创建时从反射信息构建，作为后续
 * 参数 Schema 生成与工具执行的统一入口；不可变类（final 字段 + 无 setter）。
 */
public final class ToolDefinition {

    /** 工具名，全局唯一，作为 LLM 调用的函数标识；不允许为 null 或空白。 */
    private final String name;

    /** 工具功能描述，注入 LLM 工具 Schema；可为空字符串。 */
    private final String description;

    /** 工具方法所属的 bean 实例，执行时作为调用目标；不允许为 null。 */
    private final Object bean;

    /** 工具方法引用，执行时经反射调用；不允许为 null。 */
    private final Method method;

    /**
     * @param name        工具名，不允许为 null 或空白
     * @param description 工具描述，可为空字符串
     * @param bean        所属 bean 实例，不允许为 null
     * @param method      工具方法引用，不允许为 null
     */
    public ToolDefinition(String name, String description, Object bean, Method method) {
        this.name = name;
        this.description = description;
        this.bean = bean;
        this.method = method;
    }

    /** @return 工具名 */
    public String getName() {
        return name;
    }

    /** @return 工具功能描述 */
    public String getDescription() {
        return description;
    }

    /** @return 工具方法所属的 bean 实例（执行目标） */
    public Object getBean() {
        return bean;
    }

    /** @return 工具方法引用 */
    public Method getMethod() {
        return method;
    }
}
