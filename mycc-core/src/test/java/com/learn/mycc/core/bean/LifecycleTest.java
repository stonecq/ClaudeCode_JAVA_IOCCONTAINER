package com.learn.mycc.core.bean;

import com.learn.mycc.core.context.fixture.LifecycleBean;
import com.learn.mycc.core.context.fixture.LifecycleRecorder;
import com.learn.mycc.core.context.fixture.SecondBean;
import com.learn.mycc.core.context.fixture.TrackedBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleTest {

    @BeforeEach
    void clearEvents() {
        LifecycleRecorder.clear();
    }

    @Test
    void callsInitAfterCreation() {
        BeanFactory factory = new BeanFactory();
        factory.register(BeanDefinition.from(LifecycleBean.class));
        factory.getBean(LifecycleBean.class);
        assertThat(LifecycleRecorder.EVENTS).contains("init:lifecycleBean");
    }

    @Test
    void destroysInReverseCreationOrder() {
        BeanFactory factory = new BeanFactory();
        factory.register(BeanDefinition.from(LifecycleBean.class));
        factory.register(BeanDefinition.from(SecondBean.class));
        factory.getBean(LifecycleBean.class);
        factory.getBean(SecondBean.class);
        factory.close();
        assertThat(LifecycleRecorder.EVENTS).containsExactly(
                "init:lifecycleBean", "init:secondBean", "destroy:secondBean", "destroy:lifecycleBean");
    }

    @Test
    void runsPostProcessorsInRegistrationOrder() {
        BeanFactory factory = new BeanFactory();
        List<String> calls = new ArrayList<>();
        factory.addBeanPostProcessor((bean, name) -> {
            calls.add("pp1:" + name);
            return bean;
        });
        factory.addBeanPostProcessor((bean, name) -> {
            calls.add("pp2:" + name);
            return bean;
        });
        factory.register(BeanDefinition.from(LifecycleBean.class));
        factory.getBean(LifecycleBean.class);
        assertThat(calls).containsExactly("pp1:lifecycleBean", "pp2:lifecycleBean");
    }

    @Test
    void postProcessorCanReplaceBeanInstance() {
        BeanFactory factory = new BeanFactory();
        TrackedBean replacement = new TrackedBean();
        factory.addBeanPostProcessor((bean, name) -> "trackedBean".equals(name) ? replacement : bean);
        factory.register(BeanDefinition.from(TrackedBean.class));
        assertThat(factory.getBean(TrackedBean.class)).isSameAs(replacement);
    }
}
