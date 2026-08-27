package com.learn.mycc.core.bean;

import com.learn.mycc.core.bean.fixture.ConstructorDep;
import com.learn.mycc.core.bean.fixture.ConstructorService;
import com.learn.mycc.core.bean.fixture.CycleA;
import com.learn.mycc.core.bean.fixture.CycleB;
import com.learn.mycc.core.bean.fixture.FieldDep;
import com.learn.mycc.core.bean.fixture.FieldInjectedService;
import com.learn.mycc.core.bean.fixture.MissingDepConsumer;
import com.learn.mycc.core.exception.MyccException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeanFactoryTest {

    @Test
    void injectsConstructorDependencies() {
        BeanFactory factory = factoryWith(ConstructorDep.class, ConstructorService.class);
        ConstructorService service = factory.getBean(ConstructorService.class);
        assertThat(service.getDep()).isSameAs(factory.getBean(ConstructorDep.class));
    }

    @Test
    void injectsFieldsAsFallback() {
        BeanFactory factory = factoryWith(FieldDep.class, FieldInjectedService.class);
        FieldInjectedService service = factory.getBean(FieldInjectedService.class);
        assertThat(service.getFieldDep()).isSameAs(factory.getBean(FieldDep.class));
    }

    @Test
    void returnsSingletonPerType() {
        BeanFactory factory = factoryWith(FieldDep.class);
        assertThat(factory.getBean(FieldDep.class)).isSameAs(factory.getBean(FieldDep.class));
    }

    @Test
    void failsWhenDependencyMissing() {
        BeanFactory factory = factoryWith(MissingDepConsumer.class);
        assertThatThrownBy(() -> factory.getBean(MissingDepConsumer.class))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("未注册");
    }

    @Test
    void detectsCircularDependency() {
        BeanFactory factory = factoryWith(CycleA.class, CycleB.class);
        assertThatThrownBy(() -> factory.getBean(CycleA.class))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("循环依赖");
    }

    @Test
    void rejectsDuplicateRegistration() {
        BeanFactory factory = new BeanFactory();
        factory.register(BeanDefinition.from(FieldDep.class));
        assertThatThrownBy(() -> factory.register(BeanDefinition.from(FieldDep.class)))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("重复注册");
    }

    private BeanFactory factoryWith(Class<?>... types) {
        BeanFactory factory = new BeanFactory();
        factory.register(Arrays.stream(types).map(BeanDefinition::from).toArray(BeanDefinition[]::new));
        return factory;
    }
}
