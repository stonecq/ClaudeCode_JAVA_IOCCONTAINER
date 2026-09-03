package com.learn.mycc.hooks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.hook.HookDecision;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;

import java.nio.file.Path;

/**
 * 工作区路径校验钩子：在 tool_call_before 拦截指向工作区之外的路径参数。
 *
 * <p>职责边界：拒绝绝对路径与越出工作区的相对路径（Path Traversal），保证工具
 * 只访问工作区内文件。校验策略原在工具内部，v2 起前移到 hook 层，工具保持精简。
 * 核心手段是 normalize 后校验结果是否仍以工作区根为前缀，从而识破 {@code ..}；
 * 不可执行（解析失败、无 path 参数）的调用一律放行，由后续工具体按参数缺失处理。</p>
 */
@Component
public class WorkspacePaths {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    private ApplicationConfig config;

    public WorkspacePaths() {}

    public WorkspacePaths(ApplicationConfig config) {
        this.config = config;
    }

    @Hook(event = HookEventType.TOOL_CALL_BEFORE)
    public HookDecision validate(HookEvent event) {
        if (!(event.payload() instanceof ToolCall call)) {
            return HookDecision.ALLOW;
        }
        String rawPath = pathArgument(call);
        if (rawPath == null || rawPath.isBlank()) {
            return HookDecision.ALLOW;
        }
        Path path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return HookDecision.deny("不允许绝对路径: " + rawPath);
        }
        Path root = config.getWorkspacePath().normalize();
        if (!root.resolve(path).normalize().startsWith(root)) {
            return HookDecision.deny("路径超出工作区: " + rawPath);
        }
        return HookDecision.ALLOW;
    }

    /** 从工具调用参数（JSON）中提取 path 字段；解析失败或无该字段时返回 null。 */
    private static String pathArgument(ToolCall call) {
        try {
            JsonNode node = MAPPER.readTree(call.arguments());
            JsonNode path = node.get("path");
            return path == null ? null : path.asText();
        } catch (Exception e) {
            return null;
        }
    }
}