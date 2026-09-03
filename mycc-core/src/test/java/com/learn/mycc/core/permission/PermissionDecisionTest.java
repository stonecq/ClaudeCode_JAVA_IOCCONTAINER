package com.learn.mycc.core.permission;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionDecisionTest {

    /** 常量 ALLOW：verdict=ALLOW，reason 为 null。 */
    @Test
    void allowConstantCarriesAllowVerdictAndNullReason() {
        assertThat(PermissionDecision.ALLOW.verdict()).isEqualTo(PermissionVerdict.ALLOW);
        assertThat(PermissionDecision.ALLOW.reason()).isNull();
    }

    /** 常量 ASK：verdict=ASK，reason 为 null。 */
    @Test
    void askConstantCarriesAskVerdictAndNullReason() {
        assertThat(PermissionDecision.ASK.verdict()).isEqualTo(PermissionVerdict.ASK);
        assertThat(PermissionDecision.ASK.reason()).isNull();
    }

    /** 静态工厂 deny(reason)：verdict=DENY，reason 透传。 */
    @Test
    void denyFactoryCarriesDenyVerdictAndGivenReason() {
        PermissionDecision decision = PermissionDecision.deny("高危命令需人工确认");
        assertThat(decision.verdict()).isEqualTo(PermissionVerdict.DENY);
        assertThat(decision.reason()).isEqualTo("高危命令需人工确认");
    }

    /** 判定辅助：allowed 仅当 verdict==ALLOW。 */
    @Test
    void allowedReflectsVerdict() {
        assertThat(PermissionDecision.ALLOW.allowed()).isTrue();
        assertThat(PermissionDecision.ASK.allowed()).isFalse();
        assertThat(PermissionDecision.deny("no").allowed()).isFalse();
    }
}