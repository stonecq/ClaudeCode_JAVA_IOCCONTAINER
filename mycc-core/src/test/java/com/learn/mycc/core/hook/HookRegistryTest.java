package com.learn.mycc.core.hook;

import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.hook.fixture.BadSignatureHook;
import com.learn.mycc.core.hook.fixture.RecordingHook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HookRegistryTest {

    @BeforeEach
    void clearEvents() {
        RecordingHook.clear();
    }

    @Test
    void registersHookMethodsFromBeans() {
        BeanFactory factory = new BeanFactory();
        HookRegistry registry = new HookRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(RecordingHook.class));
        factory.getBean(RecordingHook.class);

        assertThat(registry.get(HookEventType.SESSION_START)).hasSize(2);
        assertThat(registry.get(HookEventType.TOOL_CALL_BEFORE)).hasSize(1);
        assertThat(registry.get(HookEventType.SESSION_END)).isEmpty();
    }

    @Test
    void getReturnsSubscribersOnlyForRequestedEvent() {
        HookRegistry registry = new HookRegistry();
        registry.postProcessAfterInitialization(new RecordingHook(), "recorder");

        assertThat(registry.get(HookEventType.TOOL_CALL_BEFORE))
                .extracting(HookDefinition::getEventType)
                .containsExactly(HookEventType.TOOL_CALL_BEFORE);
    }

    @Test
    void rejectsWrongMethodSignature() {
        BeanFactory factory = new BeanFactory();
        HookRegistry registry = new HookRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(BadSignatureHook.class));

        assertThatThrownBy(() -> factory.getBean(BadSignatureHook.class))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("钩子方法签名");
    }
}
