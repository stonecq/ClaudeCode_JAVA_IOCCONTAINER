package com.learn.mycc.compact;

import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.provider.MockProvider;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.storage.file.WorkspaceStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompactorTest {

    @TempDir
    Path tempDir;

    Compactor compactor;
    MockProvider provider;

    @BeforeEach
    void setUp() {
        provider = MockProvider.scripted(request -> ChatResponse.text("摘要：目标是 X，剩 Y"));
        compactor = new Compactor(provider, new ConfigService(), new WorkspaceStorage(new ApplicationConfig(tempDir)));
    }

    @Test
    void estimateCharsIsPositive() {
        assertThat(compactor.estimateChars(List.of(ChatMessage.of(ChatMessage.Role.USER, "hello"))))
                .isGreaterThan(0);
    }

    @Test
    void toolResultBudgetPersistsLargeResultAndLeavesPathPreview() {
        String big = "x".repeat(250_000);
        List<ChatMessage> messages = new ArrayList<>(List.of(
                ChatMessage.assistantWithTools("", List.of(new ToolCall("c1", "read_file", "{}"))),
                ChatMessage.of(ChatMessage.Role.TOOL, big, "c1")));

        List<ChatMessage> result = compactor.toolResultBudget(messages);

        String replaced = result.get(1).content();
        assertThat(replaced).contains("已保存到").contains("c1").contains("预览");
        assertThat(replaced.length()).isLessThan(big.length());
        assertThat(Files.exists(tempDir.resolve(".mycc/compact/tool-results/c1.txt"))).isTrue();
    }

    @Test
    void toolResultBudgetNoopWhenUnderBudget() {
        List<ChatMessage> messages = new ArrayList<>(List.of(
                ChatMessage.assistantWithTools("", List.of(new ToolCall("c1", "f", "{}"))),
                ChatMessage.of(ChatMessage.Role.TOOL, "small", "c1")));

        compactor.toolResultBudget(messages);

        assertThat(messages.get(1).content()).isEqualTo("small");
    }

    @Test
    void snipCompactKeepsHeadTailAndInsertsMarker() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            messages.add(ChatMessage.of(ChatMessage.Role.USER, "m" + i));
        }

        List<ChatMessage> result = compactor.snipCompact(messages);

        assertThat(result).hasSize(50); // 3 头 + 1 标记 + 46 尾
        assertThat(result.get(3).content()).contains("已归档到").contains("transcripts");
        assertThat(result.get(4).content()).isEqualTo("m14");
    }

    @Test
    void snipCompactProtectsToolCallAndResultPairing() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.of(ChatMessage.Role.USER, "m0"));
        messages.add(ChatMessage.of(ChatMessage.Role.USER, "m1"));
        messages.add(ChatMessage.assistantWithTools("", List.of(new ToolCall("c1", "f", "{}"))));
        messages.add(ChatMessage.of(ChatMessage.Role.TOOL, "r1", "c1"));
        for (int i = 4; i < 61; i++) {
            messages.add(ChatMessage.of(ChatMessage.Role.USER, "m" + i));
        }

        List<ChatMessage> result = compactor.snipCompact(messages);

        // head 末尾的 assistant(toolCalls) 连带其 TOOL 结果一起保留，不被拆散
        ChatMessage keptAssistant = result.get(2);
        assertThat(keptAssistant.hasToolCalls()).isTrue();
        assertThat(result.get(3).role()).isEqualTo(ChatMessage.Role.TOOL);
    }

    @Test
    void compactHistoryReplacesAllWithSummaryMessage() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(ChatMessage.of(ChatMessage.Role.USER, "m" + i));
        }

        List<ChatMessage> result = compactor.compactHistory(messages, "当前请求");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content())
                .contains("Compacted")
                .contains("当前请求")
                .contains("摘要：目标是 X")
                .contains("transcripts");
    }

    @Test
    void reactiveCompactKeepsRecentMessagesAndSummarizesOlder() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(ChatMessage.of(ChatMessage.Role.USER, "m" + i));
        }

        List<ChatMessage> result = compactor.reactiveCompact(messages, "req");

        assertThat(result).hasSize(6); // 1 摘要 + 最近 5 条
        assertThat(result.get(0).content()).contains("Reactive compact");
        assertThat(result.get(1).content()).isEqualTo("m5");
    }
}