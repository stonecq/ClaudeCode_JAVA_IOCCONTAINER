package com.learn.mycc.core.bean;

import com.learn.mycc.core.bean.fixture.ConstructorDep;
import com.learn.mycc.core.bean.fixture.ConstructorService;
import com.learn.mycc.core.bean.fixture.CycleA;
import com.learn.mycc.core.bean.fixture.CycleB;
import com.learn.mycc.core.bean.fixture.FieldDep;
import com.learn.mycc.core.bean.fixture.FieldInjectedService;
import com.learn.mycc.core.annotation.ScopeType;
import com.learn.mycc.core.bean.fixture.DepConsumer;
import com.learn.mycc.core.bean.fixture.FactoryConfig;
import com.learn.mycc.core.bean.fixture.GreetingBox;
import com.learn.mycc.core.bean.fixture.ManagedService;
import com.learn.mycc.core.bean.fixture.MissingDepConsumer;
import com.learn.mycc.core.bean.fixture.NamedConsumer;
import com.learn.mycc.core.bean.fixture.ProtoBean;
import com.learn.mycc.core.bean.fixture.RecordingBpp;
import com.learn.mycc.core.context.fixture.Greeter;
import com.learn.mycc.core.context.fixture.GreeterImplA;
import com.learn.mycc.core.context.fixture.GreeterImplB;
import com.learn.mycc.core.context.fixture.LifecycleRecorder;
import com.learn.mycc.core.exception.MyccException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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

    @Test
    void registerSingletonReturnsSameInstance() {
        BeanFactory factory = new BeanFactory();
        FieldDep dependency = new FieldDep();
        factory.registerSingleton(FieldDep.class, dependency);
        assertThat(factory.getBean(FieldDep.class)).isSameAs(dependency);
        assertThat(factory.getBean(FieldDep.class)).isSameAs(factory.getBean(FieldDep.class));
    }

    @Test
    void registerSingletonRejectsNull() {
        BeanFactory factory = new BeanFactory();
        assertThatThrownBy(() -> factory.registerSingleton(FieldDep.class, null))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("不允许为 null");
    }

    @Test
    void registerSingletonConflictsWithExistingDefinition() {
        BeanFactory factory = factoryWith(FieldDep.class);
        assertThatThrownBy(() -> factory.registerSingleton(FieldDep.class, new FieldDep()))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("重复注册");
    }

    @Test
    void overrideSingletonShadowsExistingDefinition() {
        BeanFactory factory = factoryWith(GreeterImplA.class);
        GreeterImplA replacement = new GreeterImplA();
        factory.overrideSingleton(GreeterImplA.class, replacement);
        // getBean 优先返回覆盖实例而非定义实例化；接口可匹配也复用同一替代实例
        assertThat(factory.getBean(GreeterImplA.class)).isSameAs(replacement);
        assertThat(factory.getBean(Greeter.class)).isSameAs(replacement);
    }

    @Test
    void overrideSingletonRejectsNull() {
        BeanFactory factory = new BeanFactory();
        assertThatThrownBy(() -> factory.overrideSingleton(GreeterImplA.class, null))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("不允许为 null");
    }

    @Test
    void getBeanMatchesUniqueAssignableImplementation() {
        BeanFactory factory = factoryWith(GreeterImplA.class);
        assertThat(factory.getBean(Greeter.class).greet()).isEqualTo("A");
    }

    @Test
    void interfaceFallbackReusesExistingSingleton() {
        BeanFactory factory = factoryWith(GreeterImplA.class);
        GreeterImplA impl = factory.getBean(GreeterImplA.class);
        factory.preInstantiateSingletons();
        // 已按具体类型实例化后，接口回退应复用同一单例，而非再次实例化误判"多个可匹配"
        assertThat(factory.getBean(Greeter.class)).isSameAs(impl);
    }

    @Test
    void getBeanThrowsWhenMultipleAssignableImplementations() {
        BeanFactory factory = factoryWith(GreeterImplA.class, GreeterImplB.class);
        assertThatThrownBy(() -> factory.getBean(Greeter.class))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("多个可匹配");
    }

    @Test
    void getBeanThrowsWhenInterfaceHasNoImplementation() {
        BeanFactory factory = new BeanFactory();
        assertThatThrownBy(() -> factory.getBean(Greeter.class))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("未注册 Bean 类型");
    }

    @Test
    void getBeanByNameReturnsRegisteredBean() {
        BeanFactory factory = factoryWith(FieldDep.class);
        assertThat(factory.getBean("fieldDep")).isSameAs(factory.getBean(FieldDep.class));
    }

    @Test
    void getBeanByNameThrowsWhenMissing() {
        BeanFactory factory = new BeanFactory();
        assertThatThrownBy(() -> factory.getBean("nope"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("未注册 Bean 名称");
    }

    @Test
    void namedInjectionResolvesAmbiguityByName() {
        BeanFactory factory = factoryWith(GreeterImplA.class, GreeterImplB.class, NamedConsumer.class);
        assertThat(factory.getBean(NamedConsumer.class).getGreeter().greet()).isEqualTo("B");
    }

    @Test
    void prototypeBeansAreNeverCached() {
        BeanFactory factory = factoryWith(ProtoBean.class, FieldDep.class);
        assertThat(factory.getBean(ProtoBean.class)).isNotSameAs(factory.getBean(ProtoBean.class));
    }

    @Test
    void preInstantiateSkipsPrototypeAndCreatesOnDemand() {
        BeanFactory factory = factoryWith(ProtoBean.class, FieldDep.class);
        LifecycleRecorder.clear();
        factory.preInstantiateSingletons();
        assertThat(LifecycleRecorder.EVENTS).doesNotContain("init:protoBean");
        factory.getBean(ProtoBean.class);
        assertThat(LifecycleRecorder.EVENTS).contains("init:protoBean");
    }

    @Test
    void getBeanWithArgsOverridesConstructorParameterPositionally() {
        BeanFactory factory = factoryWith(GreetingBox.class);
        assertThat(factory.getBean(GreetingBox.class, "hello").message()).isEqualTo("hello");
        assertThat(factory.getBean(GreetingBox.class, "world").message()).isEqualTo("world");
    }

    @Test
    void factoryMethodsResolveParamsAndProducePostProcessedBeans() throws Exception {
        BeanFactory factory = new BeanFactory();
        factory.register(
                BeanDefinition.from(FactoryConfig.class),
                BeanDefinition.from(FieldDep.class),
                factoryBean("greeter"),
                factoryBean("depConsumer", FieldDep.class),
                factoryBean("managedService"));
        factory.addBeanPostProcessor(new RecordingBpp());
        RecordingBpp.clear();

        assertThat(factory.getBean(Greeter.class).greet()).isEqualTo("A");
        DepConsumer consumer = factory.getBean(DepConsumer.class);
        assertThat(consumer.getDep()).isSameAs(factory.getBean(FieldDep.class));
        factory.getBean(ManagedService.class);

        assertThat(RecordingBpp.PROCESSED).contains("greeter", "depConsumer", "managedService");
    }

    @Test
    void factoryDestroyMethodRunsOnClose() throws Exception {
        BeanFactory factory = new BeanFactory();
        factory.register(
                BeanDefinition.from(FactoryConfig.class),
                BeanDefinition.from(FieldDep.class),
                factoryBean("greeter"),
                factoryBean("managedService"));
        LifecycleRecorder.clear();
        factory.preInstantiateSingletons();
        factory.close();
        assertThat(LifecycleRecorder.EVENTS).containsExactly("shutdown:managedService");
    }

    private BeanFactory factoryWith(Class<?>... types) {
        BeanFactory factory = new BeanFactory();
        factory.register(Arrays.stream(types).map(BeanDefinition::from).toArray(BeanDefinition[]::new));
        return factory;
    }

    private BeanDefinition factoryBean(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = FactoryConfig.class.getDeclaredMethod(methodName, paramTypes);
        return BeanDefinition.fromFactoryMethod(FactoryConfig.class, method, methodName, ScopeType.SINGLETON);
    }
}
