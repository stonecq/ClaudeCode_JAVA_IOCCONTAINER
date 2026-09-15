package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.RecordingPort;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.compact.Compactor;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Context Compact 端到端：AgentLoop 接入压缩器的 prepare 与 reactive 重试。 */
class CompactionFlowTest {

    @TempDir
    Path tempDir;

    @Test
    void noCompactionWhenUnderLimits() {
        MockProvider provider = MockProvider.scripted(request -> ChatResponse.text("done"));
        Compactor compactor = new Compactor(provider, new ConfigService(), new ApplicationConfig(tempDir));
        Session session = Session.create();
        AgentLoop agent = AgentLoop.withToolRegistry(new RecordingPort(), provider, new ToolRegistry(),
                "mock", 10, null, session, null, compactor);

        assertThat(agent.run("hi")).isEqualTo("done");
        // 短对话不触发压缩：仅 user + assistant 两条
        assertThat(session.conversation().messages()).hasSize(2);
    }

    @Test
    void longHistoryIsSnipCompactedOnRoundStart() {
        MockProvider provider = MockProvider.scripted(request -> ChatResponse.text("done"));
        Compactor compactor = new Compactor(provider, new ConfigService(), new ApplicationConfig(tempDir));
        Session session = Session.create();
        for (int i = 0; i < 60; i++) {
            session.addMessage(Message.user("m" + i));
        }
        AgentLoop agent = AgentLoop.withToolRegistry(new RecordingPort(), provider, new ToolRegistry(),
                "mock", 10, null, session, null, compactor);

        agent.run("new");

        // 61 条被截断为「3 头 + 标记 + 46 尾」再追加本轮回答
        assertThat(session.conversation().messages().size()).isLessThan(61);
        assertThat(session.conversation().messages())
                .anySatisfy(m -> assertThat(m.content()).contains("已归档到"));
    }

    @Test
    void reactiveCompactionRetriesOnceAfterContextTooLong() {
        AtomicInteger calls = new AtomicInteger();
        MockProvider provider = MockProvider.scripted(request -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("prompt_too_long");
            }
            return ChatResponse.text("recovered");
        });
        Compactor compactor = new Compactor(provider, new ConfigService(), new ApplicationConfig(tempDir));
        Session session = Session.create();
        AgentLoop agent = AgentLoop.withToolRegistry(new RecordingPort(), provider, new ToolRegistry(),
                "mock", 10, null, session, null, compactor);

        assertThat(agent.run("hi")).isEqualTo("recovered");
        assertThat(calls.get()).isEqualTo(2);
    }
}