package com.learn.mycc.subagent;

import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.tool.ToolContext;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubagentToolsTest {

    @Test
    void subagentDelegatesToServiceAndReturnsResult() {
        SubagentService service = new SubagentService(
                MockProvider.scripted(request -> ChatResponse.text("子结果：找到了文件")),
                new ToolRegistry(), new RecordingPort(), new ConfigService());
        SubagentTools tools = new SubagentTools(service);

        String result = tools.subagent(new ToolContext("main-sess"), "在项目里找一个文件", null);

        assertThat(result).isEqualTo("子结果：找到了文件");
    }
}