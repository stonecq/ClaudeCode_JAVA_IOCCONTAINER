package com.learn.mycc.core.hook;

import com.learn.mycc.core.hook.fixture.OrderedHooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookDispatcherTest {

    @BeforeEach
    void clearCalls() {
        OrderedHooks.clear();
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

        dispatcher.dispatch(new HookEvent(HookEventType.SESSION_END, "s1", null));

        assertThat(OrderedHooks.CALLED).isEmpty();
    }
}
