package com.learn.mycc.core.context;

import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.context.fixture.Greeter;
import com.learn.mycc.core.context.fixture.GreeterImplA;
import com.learn.mycc.core.context.fixture.GreeterImplB;
import com.learn.mycc.core.context.fixture.LifecycleBean;
import com.learn.mycc.core.context.fixture.LifecycleRecorder;
import com.learn.mycc.core.context.fixture.SecondBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IocContainerTest {

    @BeforeEach
    void clearEvents() {
        LifecycleRecorder.clear();
    }

    @Test
    void startCreatesAllRegisteredBeans() {
        IocContainer container = IocContainer.create();
        container.register(BeanDefinition.from(LifecycleBean.class));
        container.register(BeanDefinition.from(SecondBean.class));
        container.start();
        assertThat(container.getBean(LifecycleBean.class)).isNotNull();
        assertThat(container.getBean(SecondBean.class)).isNotNull();
        assertThat(LifecycleRecorder.EVENTS).contains("init:lifecycleBean", "init:secondBean");
    }

    @Test
    void getBeanReturnsSameSingleton() {
        IocContainer container = IocContainer.create();
        container.register(BeanDefinition.from(SecondBean.class));
        container.start();
        assertThat(container.getBean(SecondBean.class)).isSameAs(container.getBean(SecondBean.class));
    }

    @Test
    void getBeansOfTypeReturnsAllImplementations() {
        IocContainer container = IocContainer.create();
        container.register(BeanDefinition.from(GreeterImplA.class));
        container.register(BeanDefinition.from(GreeterImplB.class));
        container.start();
        assertThat(container.getBeansOfType(Greeter.class))
                .hasSize(2)
                .extracting(Greeter::greet)
                .containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void initThenDestroyInCorrectOrder() {
        IocContainer container = IocContainer.create();
        container.register(BeanDefinition.from(LifecycleBean.class));
        container.register(BeanDefinition.from(SecondBean.class));
        container.start();
        container.close();
        assertThat(LifecycleRecorder.EVENTS).containsExactly(
                "init:lifecycleBean", "init:secondBean", "destroy:secondBean", "destroy:lifecycleBean");
    }

    @Test
    void registerScansComponentPackage() {
        IocContainer container = IocContainer.create();
        container.register("com.learn.mycc.core.context.fixture");
        container.start();
        assertThat(container.getBean(LifecycleBean.class)).isNotNull();
        assertThat(container.getBeansOfType(Greeter.class)).hasSize(2);
    }
}
