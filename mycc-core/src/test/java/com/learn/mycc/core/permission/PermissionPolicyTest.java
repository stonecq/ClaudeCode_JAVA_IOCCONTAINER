package com.learn.mycc.core.permission;

import com.learn.mycc.core.annotation.ToolRisk;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionPolicyTest {

    private final PermissionPolicy policy = new PermissionPolicy();

    /** 无规则时：LOW 风险默认放行。 */
    @Test
    void noRuleLowRiskAllows() {
        PermissionDecision decision = policy.decide(emptyStore(), "read_file", ToolRisk.LOW);
        assertThat(decision.verdict()).isEqualTo(PermissionVerdict.ALLOW);
    }

    /** 无规则时：HIGH 风险默认询问。 */
    @Test
    void noRuleHighRiskAsks() {
        PermissionDecision decision = policy.decide(emptyStore(), "bash", ToolRisk.HIGH);
        assertThat(decision.verdict()).isEqualTo(PermissionVerdict.ASK);
    }

    /** 规则命中优先于风险默认：规则 ALLOW 让 HIGH 工具放行。 */
    @Test
    void ruleAllowOverridesHighRiskDefault() {
        FakeStore store = new FakeStore();
        store.rules.put("bash", PermissionDecision.ALLOW);
        PermissionDecision decision = policy.decide(store, "bash", ToolRisk.HIGH);
        assertThat(decision.verdict()).isEqualTo(PermissionVerdict.ALLOW);
    }

    /** 规则命中优先于风险默认：规则 DENY 让 LOW 工具被拒。 */
    @Test
    void ruleDenyOverridesLowRiskDefault() {
        FakeStore store = new FakeStore();
        store.rules.put("read_file", PermissionDecision.deny("该工作区禁止读取"));
        PermissionDecision decision = policy.decide(store, "read_file", ToolRisk.LOW);
        assertThat(decision.verdict()).isEqualTo(PermissionVerdict.DENY);
        assertThat(decision.reason()).isEqualTo("该工作区禁止读取");
    }

    private static PermissionRuleStore emptyStore() {
        return new FakeStore();
    }

    /** 内存态规则存储假实现，仅供本测试。 */
    static class FakeStore implements PermissionRuleStore {
        final Map<String, PermissionDecision> rules = new HashMap<>();

        @Override
        public PermissionDecision resolve(String toolName) {
            return rules.get(toolName);
        }

        @Override
        public void remember(String toolName, PermissionDecision decision) {
            rules.put(toolName, decision);
        }

        @Override
        public void clearSession() {
            rules.clear();
        }
    }
}