package com.learn.mycc.planning;

import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.tool.ToolContext;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanToolsTest {

    @TempDir
    Path tempDir;

    PlanStore store;
    PlanTools tools;
    ToolContext ctx;

    @BeforeEach
    void setUp() {
        store = new PlanStore(new FileStorage(tempDir));
        tools = new PlanTools(store);
        ctx = new ToolContext("sess-1");
    }

    @Test
    void createPlanSavesAndReturnsFormattedPlan() {
        String result = tools.createPlan(ctx, "实现X", "设计\n编码\n测试");

        assertThat(result).contains("已创建计划").contains("目标: 实现X");
        assertThat(result).contains("1. [ ] 设计", "2. [ ] 编码", "3. [ ] 测试");
        Plan saved = store.load("sess-1").get();
        assertThat(saved.steps()).hasSize(3);
    }

    @Test
    void createPlanRejectsEmptyStepsOrGoal() {
        assertThatThrownBy(() -> tools.createPlan(ctx, "实现X", "  \n"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("步骤不能为空");
        assertThatThrownBy(() -> tools.createPlan(ctx, "  ", "设计"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("目标不能为空");
    }

    @Test
    void completeStepAdvancesPlan() {
        tools.createPlan(ctx, "实现X", "设计\n编码");

        String result = tools.completeStep(ctx, 1, "设计完成");

        assertThat(result).contains("已推进");
        assertThat(store.load("sess-1").get().steps().get(0).done()).isTrue();
        assertThat(store.load("sess-1").get().steps().get(1).done()).isFalse();
    }

    @Test
    void completeStepAllDoneReturnsCompletionAndClearsPlan() {
        tools.createPlan(ctx, "实现X", "设计");
        String result = tools.completeStep(ctx, 1, "完成");

        assertThat(result).contains("全部步骤完成");
        // 任务完成即清理，同会话不再残留旧计划
        assertThat(store.load("sess-1")).isEmpty();
    }

    @Test
    void completeStepBeforeAllDoneKeepsPlan() {
        tools.createPlan(ctx, "实现X", "设计\n编码");
        tools.completeStep(ctx, 1, "第一步");
        assertThat(store.load("sess-1")).isPresent();
        assertThat(store.load("sess-1").get().steps().get(0).done()).isTrue();
    }

    @Test
    void completeStepOutOfBoundsThrows() {
        tools.createPlan(ctx, "实现X", "设计");
        assertThatThrownBy(() -> tools.completeStep(ctx, 2, "x"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("越界");
    }

    @Test
    void completeStepWithoutPlanThrows() {
        assertThatThrownBy(() -> tools.completeStep(ctx, 1, "x"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("没有进行中的计划");
    }

    @Test
    void readPlanReturnsFullPlanState() {
        tools.createPlan(ctx, "实现X", "设计\n编码");
        tools.completeStep(ctx, 1, "第一步");

        String result = tools.readPlan(ctx);

        assertThat(result).contains("当前计划", "目标: 实现X");
        assertThat(result).contains("1. [x] 设计", "2. [ ] 编码");
    }

    @Test
    void readPlanWithoutPlanReturnsHintText() {
        assertThat(tools.readPlan(ctx)).contains("没有进行中的计划");
    }

    @Test
    void readPlanAfterAllDoneFallsBackToHint() {
        tools.createPlan(ctx, "实现X", "设计");
        tools.completeStep(ctx, 1, "完成");
        assertThat(tools.readPlan(ctx)).contains("没有进行中的计划");
    }
}