package com.learn.mycc.agent.planning;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.tool.ToolContext;

import java.util.List;

/**
 * 规划工具：暴露给 LLM 的 {@code create_plan / read_plan / complete_step}，支撑"先生成多步
 * 计划、再逐步执行、核对推进"的规划闭环。
 * <p>计划按会话存于 {@link PlanStore}，工具经 {@link ToolContext} 感知当前会话（由
 * 执行器注入，不暴露给 LLM）。create_plan 落盘计划并返回文本，LLM 据此逐步执行；
 * read_plan 供长会话/多轮迭代后随时重新读取计划全貌；每完成一步调 complete_step
 * （序号 1 起）推进，同步返回最新计划状态。</p>
 */
@Component
public class PlanTools {

    private final PlanStore store;

    @Inject
    public PlanTools(PlanStore store) {
        this.store = store;
    }

    /** 创建计划：按会话覆盖保存，返回计划文本供逐步执行。 */
    @Tool(name = "create_plan", description = "为当前任务创建多步计划：落盘并按步骤逐步执行，每完成一步调 complete_step 推进", subagentExcluded = true)
    public String createPlan(ToolContext ctx,
                             @ToolParam(description = "任务目标") String goal,
                             @ToolParam(description = "步骤列表，每行一步、按执行顺序") String steps) {
        List<String> stepList = steps.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (goal == null || goal.isBlank()) {
            throw new MyccException("计划目标不能为空");
        }
        if (stepList.isEmpty()) {
            throw new MyccException("计划步骤不能为空");
        }
        Plan plan = Plan.of(goal, stepList);
        store.save(ctx.sessionId(), plan);
        return "已创建计划，逐步执行并每完成一步调用 complete_step(序号) 推进：\n" + format(plan);
    }

    /** 推进计划：把第 stepIndex（1 起）步标记完成，返回最新计划状态。 */
    @Tool(name = "complete_step", description = "标记计划第 N 步完成（序号从 1 起），返回剩余计划；全部完成时返回完成提示", subagentExcluded = true)
    public String completeStep(ToolContext ctx,
                               @ToolParam(description = "步骤序号，从 1 开始") int stepIndex,
                               @ToolParam(description = "该步完成说明", required = false) String note) {
        Plan plan = store.load(ctx.sessionId())
                .orElseThrow(() -> new MyccException("当前会话没有进行中的计划，请先用 create_plan 创建"));
        Plan updated = plan.markDone(stepIndex - 1);
        if (updated.allDone()) {
            // 任务完成即清理计划文件，避免残留与续聊读到旧计划
            store.delete(ctx.sessionId());
            return "全部步骤完成，计划已达成：\n" + format(updated);
        }
        store.save(ctx.sessionId(), updated);
        return "已推进，当前计划：\n" + format(updated);
    }

    /** 读取计划：返回当前会话计划全貌；读取是查询，无计划返回提示文本而非报错，引导先创建。 */
    @Tool(name = "read_plan", description = "查看当前会话的完整计划：目标 + 每步状态（已完成/未完成）", subagentExcluded = true)
    public String readPlan(ToolContext ctx) {
        return store.load(ctx.sessionId())
                .map(plan -> "当前计划：\n" + format(plan))
                .orElse("当前会话没有进行中的计划，如需规划请先用 create_plan 创建");
    }

    /** 格式化计划：目标 + 每步「序号 [x]/[ ] 描述」。 */
    private static String format(Plan plan) {
        StringBuilder sb = new StringBuilder("目标: ").append(plan.goal());
        for (int i = 0; i < plan.steps().size(); i++) {
            PlanStep step = plan.steps().get(i);
            sb.append("\n").append(i + 1).append(". ").append(step.done() ? "[x] " : "[ ] ").append(step.description());
        }
        return sb.toString();
    }
}