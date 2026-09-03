package com.learn.mycc.core.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 钩子派发器：把事件按注册顺序同步派发给全部订阅者。
 * 设计意图：单个钩子失败（抛异常）只记录告警、不中断后续钩子，也不向调用方抛异常——
 * 对应 PRD FR-8「失败不影响主流程」。异步执行留待后续按需扩展（YAGNI）。
 */
public final class HookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(HookDispatcher.class);

    /** 钩子注册表：按事件类型查找订阅者。 */
    private final HookRegistry registry;

    /** @param registry 钩子注册表，不允许为 null */
    public HookDispatcher(HookRegistry registry) {
        this.registry = registry;
    }

    /**
     * 派发事件给该事件的全部订阅者（按注册顺序）。
     * 单个钩子抛出的任何异常都会被捕获并记录告警，不向外传播。
     *
     * @param event 要派发的事件，不允许为 null
     */
    public void dispatch(HookEvent event) {
        for (HookDefinition definition : registry.get(event.type())) {
            try {
                definition.getMethod().invoke(definition.getBean(), event);
            } catch (Exception e) {
                // 捕获全部异常（含反射包装与被调方法抛出的运行期异常），保证一个钩子失败不影响其余。
                log.warn("钩子执行失败: {}", event.type().eventName(), e);
            }
        }
    }
}
