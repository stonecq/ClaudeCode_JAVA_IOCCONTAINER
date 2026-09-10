package com.learn.mycc.agent.planning;

import com.learn.mycc.core.exception.MyccException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanTest {

    @Test
    void ofCreatesAllPendingStepsAndFiltersBlankLines() {
        Plan plan = Plan.of("实现X", List.of("设计", " ", "编码", "测试"));
        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps()).allMatch(step -> !step.done());
        assertThat(plan.nextIndex()).isZero();
        assertThat(plan.allDone()).isFalse();
    }

    @Test
    void markDoneMarksSpecificIndexAndReturnsNewInstance() {
        Plan plan = Plan.of("实现X", List.of("设计", "编码", "测试"));
        Plan updated = plan.markDone(0);

        assertThat(updated.steps().get(0).done()).isTrue();
        assertThat(updated.steps().get(1).done()).isFalse();
        // 原计划不变（不可变）
        assertThat(plan.steps().get(0).done()).isFalse();
        assertThat(updated.nextIndex()).isEqualTo(1);
    }

    @Test
    void markDoneOutOfBoundsThrows() {
        Plan plan = Plan.of("实现X", List.of("设计"));
        assertThatThrownBy(() -> plan.markDone(1))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("越界");
        assertThatThrownBy(() -> plan.markDone(-1))
                .isInstanceOf(MyccException.class);
    }

    @Test
    void allDoneWhenEveryStepCompleted() {
        Plan plan = Plan.of("实现X", List.of("设计", "编码"));
        Plan done = plan.markDone(0).markDone(1);
        assertThat(done.allDone()).isTrue();
        assertThat(done.nextIndex()).isEqualTo(-1);
    }
}