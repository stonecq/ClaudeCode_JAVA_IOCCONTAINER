package com.learn.mycc.core.tool;

import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.tool.fixture.CalculatorTool;
import com.learn.mycc.core.tool.fixture.DuplicateToolA;
import com.learn.mycc.core.tool.fixture.DuplicateToolB;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    @Test
    void registersToolMethodsFromBeans() {
        BeanFactory factory = new BeanFactory();
        ToolRegistry registry = new ToolRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(CalculatorTool.class));
        factory.getBean(CalculatorTool.class);
        assertThat(registry.getAll()).hasSize(2);
        assertThat(registry.get("add").getDescription()).isEqualTo("两个数相加");
        assertThat(registry.get("subtract").getBean()).isNotNull();
        assertThat(registry.get("subtract").getMethod()).isNotNull();
    }

    @Test
    void containerExposesRegisteredTools() {
        IocContainer container = IocContainer.create();
        container.register(BeanDefinition.from(CalculatorTool.class));
        container.start();
        assertThat(container.getToolRegistry().getAll()).hasSize(2);
        assertThat(container.getToolRegistry().get("add").getBean())
                .isSameAs(container.getBean(CalculatorTool.class));
    }

    @Test
    void rejectsDuplicateToolNameAcrossBeans() {
        BeanFactory factory = new BeanFactory();
        ToolRegistry registry = new ToolRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(DuplicateToolA.class));
        factory.register(BeanDefinition.from(DuplicateToolB.class));
        factory.getBean(DuplicateToolA.class);
        assertThatThrownBy(() -> factory.getBean(DuplicateToolB.class))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("重复注册工具");
    }

    @Test
    void rejectsDirectDuplicateRegistration() throws Exception {
        Method method = CalculatorTool.class.getDeclaredMethod("add", int.class, int.class);
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolDefinition("add", "desc", new CalculatorTool(), method));
        assertThatThrownBy(() -> registry.register(new ToolDefinition("add", "desc", new CalculatorTool(), method)))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("重复注册工具");
    }

    @Test
    void getThrowsForUnknownTool() {
        ToolRegistry registry = new ToolRegistry();
        assertThatThrownBy(() -> registry.get("missing"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("未注册工具");
    }

    @Test
    void capturesSubagentExcludedFlagFromAnnotation() {
        BeanFactory factory = new BeanFactory();
        ToolRegistry registry = new ToolRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(ExcludedFixture.class));
        factory.getBean(ExcludedFixture.class);

        assertThat(registry.get("sub").isSubagentExcluded()).isTrue();
        assertThat(registry.get("open").isSubagentExcluded()).isFalse();
    }

    static final class ExcludedFixture {
        @Tool(name = "sub", description = "禁止子代理", subagentExcluded = true)
        public String sub(String x) {
            return "";
        }

        @Tool(name = "open", description = "默认开放")
        public String open(String x) {
            return "";
        }
    }
}
