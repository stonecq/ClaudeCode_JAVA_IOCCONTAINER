package com.learn.mycc.core.hook;

import com.learn.mycc.core.hook.fixture.DecisionHooks;
import com.learn.mycc.core.hook.fixture.OrderedHooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookDispatcherTest {

    @BeforeEach
    void clearCalls() {
        OrderedHooks.clear();
        DecisionHooks.clear();
    }

    @Test
    void dispatchesInRegistrationOrder() throws Exception {
        HookRegistry registry = new HookRegistry();
        OrderedHooks bean = new OrderedHooks();
        registry.register(new HookDefinition(HookEventType.SESSION_START, bean,
                OrderedHooks.class.getDeclaredMethod("first", HookEvent.class)));
        registry.register(new HookDefinition(HookEventType.SESSION_START, bean,
                OrderedHooks.class.getDeclaredMethod("second", HookEvent.class)));
        HookDispatcher dispatcher = new HookDispatcher(registry);

        dispatcher.dispatch(new HookEvent(HookEventType.SESSION_START, "s1", null));

        assertThat(OrderedHooks.CALLED).containsExactly("first", "second");
    }

    @Test
    void continuesAfterHookFailure() throws Exception {
        HookRegistry registry = new HookRegistry();
        OrderedHooks bean = new OrderedHooks();
        registry.register(new HookDefinition(HookEventType.SESSION_START, bean,
                OrderedHooks.class.getDeclaredMethod("boom", HookEvent.class)));
        registry.register(new HookDefinition(HookEventType.SESSION_START, bean,
                OrderedHooks.class.getDeclaredMethod("after", HookEvent.class)));
        HookDispatcher dispatcher = new HookDispatcher(registry);

        dispatcher.dispatch(new HookEvent(HookEventType.SESSION_START, "s1", null));

        assertThat(OrderedHooks.CALLED).containsExactly("after");
    }

    @Test
    void noopForEventWithoutSubscribers() {
        HookDispatcher dispatcher = new HookDispatcher(new HookRegistry());

        HookDecision decision = dispatcher.dispatch(new HookEvent(HookEventType.SESSION_END, "s1", null));

        assertThat(OrderedHooks.CALLED).isEmpty();
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void returnsAllowWhenNoSubscriberDenies() throws Exception {
        HookRegistry registry = new HookRegistry();
        DecisionHooks bean = new DecisionHooks();
        registry.register(new HookDefinition(HookEventType.TOOL_CALL_BEFORE, bean,
                DecisionHooks.class.getDeclaredMethod("allow", HookEvent.class)));
        HookDispatcher dispatcher = new HookDispatcher(registry);

        HookDecision decision = dispatcher.dispatch(new HookEvent(HookEventType.TOOL_CALL_BEFORE, "s1", null));

        assertThat(decision.allowed()).isTrue();
        assertThat(DecisionHooks.CALLED).containsExactly("allow");
    }

    @Test
    void shortCircuitsOnFirstDeny() throws Exception {
        HookRegistry registry = new HookRegistry();
        DecisionHooks bean = new DecisionHooks();
        registry.register(new HookDefinition(HookEventType.TOOL_CALL_BEFORE, bean,
                DecisionHooks.class.getDeclaredMethod("deny", HookEvent.class)));
        // 首个订阅者已拒绝，后续订阅者不应再被调用
        registry.register(new HookDefinition(HookEventType.TOOL_CALL_BEFORE, bean,
                DecisionHooks.class.getDeclaredMethod("afterDeny", HookEvent.class)));
        HookDispatcher dispatcher = new HookDispatcher(registry);

        HookDecision decision = dispatcher.dispatch(new HookEvent(HookEventType.TOOL_CALL_BEFORE, "s1", null));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("blocked");
        assertThat(DecisionHooks.CALLED).containsExactly("deny");
    }

    @Test
    void allowsWhenSubscriberThrows() throws Exception {
        HookRegistry registry = new HookRegistry();
        OrderedHooks bean = new OrderedHooks();
        registry.register(new HookDefinition(HookEventType.TOOL_CALL_BEFORE, bean,
                OrderedHooks.class.getDeclaredMethod("boom", HookEvent.class)));
        HookDispatcher dispatcher = new HookDispatcher(registry);

        HookDecision decision = dispatcher.dispatch(new HookEvent(HookEventType.TOOL_CALL_BEFORE, "s1", null));

        // 抛异常的钩子视为观察者异常：不否决，归约为放行
        assertThat(decision.allowed()).isTrue();
    }
}