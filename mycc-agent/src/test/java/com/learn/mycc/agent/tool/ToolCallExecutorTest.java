package com.learn.mycc.agent.tool;

import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallExecutorTest {

    private ToolRegistry registry;
    private ToolCallExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new TestTools(), "testTools");
        executor = new ToolCallExecutor(registry);
    }

    @Test
    void bindsArgumentsAndInvokes() {
        ToolResult result = executor.execute(new ToolCall("c1", "add", "{\"a\":1,\"b\":2}"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("3");
    }

    @Test
    void bindsStringParameter() {
        ToolResult result = executor.execute(new ToolCall("c2", "greet", "{\"name\":\"bob\"}"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("hello bob");
    }

    @Test
    void reportsUnknownToolAsFailure() {
        ToolResult result = executor.execute(new ToolCall("c3", "nope", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("未注册工具");
    }

    @Test
    void capturesToolExceptionAsFailure() {
        ToolResult result = executor.execute(new ToolCall("c4", "boom", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.output()).isEqualTo("kaboom");
    }

    @Test
    void reportsMissingPrimitiveParameterAsFailure() {
        ToolResult result = executor.execute(new ToolCall("c5", "add", "{\"a\":1}"));

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("缺少参数");
    }

    static final class TestTools {

        @Tool(name = "add", description = "两个整数相加")
        public int add(int a, int b) {
            return a + b;
        }

        @Tool(name = "greet", description = "问候")
        public String greet(String name) {
            return "hello " + name;
        }

        @Tool(name = "boom", description = "抛异常")
        public String boom() {
            throw new IllegalStateException("kaboom");
        }
    }
}
