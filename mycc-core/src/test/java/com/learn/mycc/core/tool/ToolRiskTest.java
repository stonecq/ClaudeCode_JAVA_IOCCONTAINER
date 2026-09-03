package com.learn.mycc.core.tool;

import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolRisk;
import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRiskTest {

    /** 构造器风险默认/透传：4 参构造缺省 LOW，5 参构造透传声明值。 */
    @Test
    void definitionRiskDefaultsLowAndPassesExplicitValue() throws Exception {
        java.lang.reflect.Method method = ToolRiskTest.NestedTool.class.getDeclaredMethod("danger");
        ToolDefinition defaulted = new ToolDefinition("a", "desc", new NestedTool(), method);
        ToolDefinition explicit = new ToolDefinition("a", "desc", new NestedTool(), method, ToolRisk.HIGH);
        assertThat(defaulted.getRisk()).isEqualTo(ToolRisk.LOW);
        assertThat(explicit.getRisk()).isEqualTo(ToolRisk.HIGH);
    }

    /** 注册表从 @Tool 注解推导风险：默认 LOW，显式 HIGH 透传。 */
    @Test
    void registryDerivesRiskFromAnnotation() {
        BeanFactory factory = new BeanFactory();
        ToolRegistry registry = new ToolRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(NestedTool.class));
        factory.getBean(NestedTool.class);
        assertThat(registry.get("safe_op").getRisk()).isEqualTo(ToolRisk.LOW);
        assertThat(registry.get("danger_op").getRisk()).isEqualTo(ToolRisk.HIGH);
    }

    static class NestedTool {
        @Tool(name = "safe_op", description = "安全操作")
        public String safe() {
            return "ok";
        }

        @Tool(name = "danger_op", description = "危险操作", risk = ToolRisk.HIGH)
        public String danger() {
            return "boom";
        }
    }
}