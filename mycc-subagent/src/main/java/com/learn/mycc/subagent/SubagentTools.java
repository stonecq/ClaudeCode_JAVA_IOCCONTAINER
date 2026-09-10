package com.learn.mycc.subagent;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.tool.ToolContext;

/**
 * 子代理工具：暴露 {@code subagent} 给主代理，派发子任务给异步独立上下文的子代理。
 * 结果即子代理最终文本，作为工具结果回填给主代理继续处理。
 */
@Component
public class SubagentTools {

    private final SubagentService service;

    @Inject
    public SubagentTools(SubagentService service) {
        this.service = service;
    }

    /** 派发子任务：子代理独立上下文 + 文件检索工具，返回其最终结果。 */
    @Tool(name = "subagent", description = "派发子任务给子代理（独立上下文 + 文件检索工具），返回其最终结果",
            subagentExcluded = true)
    public String subagent(ToolContext ctx,
                           @ToolParam(description = "子任务指令") String task,
                           @ToolParam(description = "子代理可选系统提示（约束行为）", required = false) String systemPrompt) {
        return service.run(task, systemPrompt);
    }
}