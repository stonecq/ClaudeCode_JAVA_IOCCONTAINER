package com.learn.mycc.core.context;

import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.hook.fixture.RecordingHook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IocContainerHookTest {

    @Test
    void containerExposesRegisteredHooks() {
        IocContainer container = IocContainer.create();
        container.register(BeanDefinition.from(RecordingHook.class));
        container.start();

        assertThat(container.getHookRegistry().get(HookEventType.SESSION_START)).hasSize(2);
        assertThat(container.getHookRegistry().get(HookEventType.TOOL_CALL_BEFORE)).hasSize(1);
    }
}
