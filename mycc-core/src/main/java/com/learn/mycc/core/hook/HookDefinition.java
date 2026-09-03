package com.learn.mycc.core.hook;

import java.lang.reflect.Method;

/**
 * 钩子定义：@Hook 方法的元数据快照（事件类型、所属 bean 与方法引用）。
 * 由 {@link HookRegistry} 在 bean 创建时从反射信息构建，作为后续派发的统一入口；
 * 不可变类（final 字段 + 无 setter）。
 */
public final class HookDefinition {

    /** 订阅的事件类型，决定该钩子何时被派发；不允许为 null。 */
    private final HookEventType eventType;

    /** 钩子方法所属的 bean 实例，派发时作为调用目标；不允许为 null。 */
    private final Object bean;

    /** 钩子方法引用（签名：单个 HookEvent 参数），派发时经反射调用；不允许为 null。 */
    private final Method method;

    /**
     * @param eventType 订阅的事件类型，不允许为 null
     * @param bean      所属 bean 实例，不允许为 null
     * @param method    钩子方法引用，不允许为 null
     */
    public HookDefinition(HookEventType eventType, Object bean, Method method) {
        this.eventType = eventType;
        this.bean = bean;
        this.method = method;
    }

    /** @return 订阅的事件类型 */
    public HookEventType getEventType() {
        return eventType;
    }

    /** @return 所属 bean 实例（调用目标） */
    public Object getBean() {
        return bean;
    }

    /** @return 钩子方法引用 */
    public Method getMethod() {
        return method;
    }
}
