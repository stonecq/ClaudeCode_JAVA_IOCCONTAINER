package com.learn.mycc.subagent;

import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolSpec;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SubagentServiceTest {

    /** 注册表夹具：含两个默认开放工具 + 两个 excluded（subagent / memory），验证子代理只收非 excluded。 */
    static final class TestTools {
        @Tool(name = "glob", description = "glob 搜索")
        public String glob(String pattern) {
            return "";
        }

        @Tool(name = "grep", description = "grep 搜索")
        public String grep(String regex, String path) {
            return "";
        }

        @Tool(name = "subagent", description = "子代理", subagentExcluded = true)
        public String subagent(String task) {
            return "";
        }

        @Tool(name = "read_memory", description = "读记忆", subagentExcluded = true)
        public String readMemory(String id) {
            return "";
        }
    }

    private SubagentService service(ToolRegistry registry, AtomicReference<ChatRequest> captured) {
        MockProvider provider = MockProvider.scripted(request -> {
            captured.set(request);
            return ChatResponse.text("子结果：找到文件");
        });
        return new SubagentService(provider, registry, new RecordingPort(), new ConfigService());
    }

    @Test
    void subagentSeesOnlyNonExcludedTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new TestTools(), "testTools");
        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        SubagentService service = service(registry, captured);

        String result = service.run("在项目里找一个文件", null);

        assertThat(result).isEqualTo("子结果：找到文件");
        // 子代理请求发给 LLM 的工具只含非 excluded（glob/grep），excluded 工具（subagent/memory）被过滤
        assertThat(captured.get().tools()).extracting(ToolSpec::name)
                .containsExactly("glob", "grep");
    }

    @Test
    void subagentRunUsesDefaultSystemPromptWhenNoneProvided() {
        ToolRegistry registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new TestTools(), "testTools");
        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        SubagentService service = service(registry, captured);

        service.run("任务", null);

        assertThat(captured.get().messages().get(0).content())
                .contains(SubagentService.DEFAULT_SUBAGENT_PROMPT);
    }

    @Test
    void subagentRunUsesCustomSystemPromptWhenProvided() {
        ToolRegistry registry = new ToolRegistry();
        registry.postProcessAfterInitialization(new TestTools(), "testTools");
        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        SubagentService service = service(registry, captured);

        service.run("任务", "你只做只读分析，不修改文件");

        assertThat(captured.get().messages().get(0).content())
                .contains("只做只读分析");
    }

    @Test
    void nestedRunWithNoWhitelistToolsStillCompletes() {
        // 空注册表 → 子代理工具集为空，但可直接给出文本结论
        AtomicReference<ChatRequest> captured = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            captured.set(request);
            return ChatResponse.text("纯文本子结果");
        });
        SubagentService service = new SubagentService(provider, new ToolRegistry(), new RecordingPort(), new ConfigService());

        String result = service.run("只需回答", null);

        assertThat(result).isEqualTo("纯文本子结果");
        assertThat(captured.get().tools()).isEmpty();
    }
}