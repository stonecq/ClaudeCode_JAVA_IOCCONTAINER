package com.learn.mycc.core.hook;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 钩子派发器：把事件按注册顺序同步派发给全部订阅者。
 * 设计意图：单个钩子失败（抛异常）只记录告警、不中断后续钩子，也不向调用方抛异常——
 * 对应 PRD FR-8「失败不影响主流程」。异步执行留待后续按需扩展（YAGNI）。
 * 可否决事件（如 tool_call_before）的订阅者可返回 {@link HookDecision}：任一订阅者
 * 拒绝即整体短路为拒绝；普通 void 钩子仍按观察者执行，不影响决策。
 */
@Component
public final class HookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(HookDispatcher.class);

    /** 钩子注册表：按事件类型查找订阅者。 */
    private final HookRegistry registry;

    /** @param registry 钩子注册表，不允许为 null */
    @Inject
    public HookDispatcher(HookRegistry registry) {
        this.registry = registry;
    }

    /**
     * 派发事件给该事件的全部订阅者（按注册顺序）。
     * 订阅者返回的 {@link HookDecision} 拒绝首个即短路返回；无拒绝（含 void 钩子、
     * 抛出异常被捕获）时归约为 {@link HookDecision#ALLOW}。
     *
     * @param event 要派发的事件，不允许为 null
     * @return 汇总后的决策；任一订阅者拒绝时为该拒绝，否则为 ALLOW
     */
    public HookDecision dispatch(HookEvent event) {
        for (HookDefinition definition : registry.get(event.type())) {
            try {
                Object result = definition.getMethod().invoke(definition.getBean(), event);
                if (result instanceof HookDecision decision && !decision.allowed()) {
                    return decision;
                }
            } catch (Exception e) {
                // 捕获全部异常（含反射包装与被调方法抛出的运行期异常），保证一个钩子失败不影响其余。
                log.warn("钩子执行失败: {}", event.type().eventName(), e);
            }
        }
        return HookDecision.ALLOW;
    }
}
