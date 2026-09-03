package com.learn.mycc.hooks;

import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.hook.HookDecision;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspacePathsTest {

    @TempDir
    Path workspace;

    private WorkspacePaths hook;

    @BeforeEach
    void setUp() {
        hook = new WorkspacePaths(new ApplicationConfig(workspace));
    }

    private HookDecision decide(ToolCall call) {
        return hook.validate(new HookEvent(HookEventType.TOOL_CALL_BEFORE, "s1", call));
    }

    @Test
    void allowsRelativePathInsideWorkspace() {
        assertThat(decide(callWith("notes/a.txt")).allowed()).isTrue();
    }

    @Test
    void allowsParentSegmentThatStaysInside() {
        // src/../notes.txt 归一化后仍在工作区内，与原工具语义一致：放行
        assertThat(decide(callWith("src/../notes.txt")).allowed()).isTrue();
    }

    @Test
    void deniesAbsolutePath() {
        HookDecision decision = decide(callWith(workspace.resolve("f.txt").toString()));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("不允许绝对路径");
    }

    @Test
    void deniesTraversalOutsideWorkspace() {
        HookDecision decision = decide(callWith("../../outside.txt"));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("路径超出工作区");
    }

    @Test
    void allowsToolCallWithoutPathArgument() {
        ToolCall call = new ToolCall("c1", "echo", "{\"text\":\"hi\"}");
        assertThat(decide(call).allowed()).isTrue();
    }

    @Test
    void allowsMalformedArguments() {
        // 参数非法 JSON：无法提取 path，视同无路径参数放行，由工具体按参数缺失处理
        ToolCall call = new ToolCall("c1", "read_file", "not-json");
        assertThat(decide(call).allowed()).isTrue();
    }

    @Test
    void allowsNonToolCallPayload() {
        assertThat(hook.validate(new HookEvent(HookEventType.TOOL_CALL_BEFORE, "s1", "text")).allowed()).isTrue();
    }

    private static ToolCall callWith(String path) {
        // JSON 字符串中反斜杠需转义；Windows 绝对路径含 \，不转义会解析失败
        String escaped = path.replace("\\", "\\\\");
        return new ToolCall("c1", "read_file", "{\"path\":\"" + escaped + "\"}");
    }
}