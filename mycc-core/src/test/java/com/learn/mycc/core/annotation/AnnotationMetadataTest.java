package com.learn.mycc.core.annotation;

import com.learn.mycc.core.hook.HookEventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationMetadataTest {

    @Test
    void toolAnnotationExposesNameAndDescription() throws Exception {
        var method = SampleTool.class.getMethod("greet", String.class);
        Tool tool = method.getAnnotation(Tool.class);
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("greet");
        assertThat(tool.description()).isEqualTo("问候用户");

        ToolParam param = method.getParameters()[0].getAnnotation(ToolParam.class);
        assertThat(param).isNotNull();
        assertThat(param.description()).isEqualTo("用户名字");
        assertThat(param.required()).isTrue();
    }

    @Test
    void hookAnnotationIsReservedForV2() throws Exception {
        var method = SampleTool.class.getMethod("onEvent");
        Hook hook = method.getAnnotation(Hook.class);
        assertThat(hook).isNotNull();
        assertThat(hook.event()).isEqualTo(HookEventType.SESSION_START);
    }

    @Test
    void componentIsRuntimeRetained() {
        assertThat(SampleComponent.class.isAnnotationPresent(Component.class)).isTrue();
    }

    public static class SampleTool {

        @Tool(name = "greet", description = "问候用户")
        public String greet(@ToolParam(description = "用户名字") String name) {
            return "hi " + name;
        }

        @Hook(event = HookEventType.SESSION_START)
        public void onEvent() {
        }
    }

    @Component
    public static class SampleComponent {
    }
}
