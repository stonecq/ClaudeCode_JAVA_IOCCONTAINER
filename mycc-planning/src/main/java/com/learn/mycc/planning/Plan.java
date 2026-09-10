package com.learn.mycc.planning;

import com.learn.mycc.core.exception.MyccException;

import java.util.ArrayList;
import java.util.List;

/**
 * 多步计划：目标 + 有序步骤列表（每步含完成标记）。
 * 不可变（compact constructor 冻结 steps 列表）；推进通过 {@link #markDone} 返回
 * 标记某步完成后的新实例，存储层以替换方式落盘。
 *
 * @param goal  计划目标
 * @param steps 有序步骤列表（非空；越界由 {@link #markDone} 抛错）
 */
public record Plan(String goal, List<PlanStep> steps) {

    public Plan {
        steps = List.copyOf(steps);
    }

    /** 由目标与步骤描述列表构造计划，所有步骤初始未完成；空描述行被过滤。 */
    public static Plan of(String goal, List<String> stepDescriptions) {
        return new Plan(goal, stepDescriptions.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> new PlanStep(s.trim(), false))
                .toList());
    }

    /** @return 是否全部步骤已完成（含无步骤计划的边角情形）。 */
    public boolean allDone() {
        return steps.stream().allMatch(PlanStep::done);
    }

    /** @return 首个未完成步骤的下标；全部完成返回 -1。 */
    public int nextIndex() {
        for (int i = 0; i < steps.size(); i++) {
            if (!steps.get(i).done()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 把指定下标步骤标记为完成，返回新实例（不改动原计划）。
     *
     * @param index 步骤下标（0 起）
     * @return 标记完成后的新计划
     * @throws MyccException 下标越界时抛出
     */
    public Plan markDone(int index) {
        if (index < 0 || index >= steps.size()) {
            throw new MyccException("步骤序号越界: " + (index + 1));
        }
        List<PlanStep> updated = new ArrayList<>(steps);
        updated.set(index, new PlanStep(steps.get(index).description(), true));
        return new Plan(goal, updated);
    }
}