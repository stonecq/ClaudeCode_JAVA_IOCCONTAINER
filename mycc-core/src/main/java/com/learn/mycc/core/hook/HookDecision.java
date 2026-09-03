package com.learn.mycc.core.hook;

/**
 * 钩子决策：订阅者在可否决事件（如 tool_call_before）上的输出。
 * 允许时返回 {@link #ALLOW}；拒绝时用 {@link #deny} 携带原因，供调用方回填给 agent。
 * 由 {@link HookDispatcher} 汇总：任一订阅者给出 deny 即短路为拒绝。
 */
public record HookDecision(boolean allowed, String reason) {

    /** 放行常量；任何未阻止的派发最终都归约为该值。 */
    public static final HookDecision ALLOW = new HookDecision(true, null);

    /**
     * 构造一个拒绝决策。
     *
     * @param reason 拒绝原因，将回填给 agent 说明为何被拦截
     * @return 拒绝决策
     */
    public static HookDecision deny(String reason) {
        return new HookDecision(false, reason);
    }
}