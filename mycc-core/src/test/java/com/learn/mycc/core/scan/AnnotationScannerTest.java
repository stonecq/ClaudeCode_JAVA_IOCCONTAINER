package com.learn.mycc.core.scan;

import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.scan.fixture.Greeter;
import com.learn.mycc.core.scan.fixture.SupportService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationScannerTest {

    private final AnnotationScanner scanner = new AnnotationScanner(getClass().getClassLoader());

    @Test
    void scansOnlyComponentClasses() {
        List<Class<?>> components = scanner.scanComponents("com.learn.mycc.core.scan.fixture");
        assertThat(components).extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("Greeter", "SupportService");
    }

    @Test
    void convertsScannedClassesToBeanDefinitions() {
        List<BeanDefinition> definitions = scanner.scanBeanDefinitions("com.learn.mycc.core.scan.fixture");
        assertThat(definitions).extracting(BeanDefinition::getName)
                .containsExactlyInAnyOrder("greeter", "supportService");
    }

    @Test
    void beanDefinitionCapturesConstructorInjection() {
        BeanDefinition def = BeanDefinition.from(Greeter.class);
        assertThat(def.getName()).isEqualTo("greeter");
        assertThat(def.getInjectionConstructor()).isNotNull();
        assertThat(def.getInjectionConstructor().getParameterTypes()).containsExactly(SupportService.class);
        assertThat(def.getInjectFields()).isEmpty();
    }
}
