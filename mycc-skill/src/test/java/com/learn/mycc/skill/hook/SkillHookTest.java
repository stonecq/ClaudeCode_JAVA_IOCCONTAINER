package com.learn.mycc.skill.hook;

import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.core.skill.SkillDefinition;
import com.learn.mycc.core.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillHookTest {

    SkillRegistry registry;
    SkillHook hook;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
        hook = new SkillHook(registry);
    }

    @Test
    void injectsCatalogIntoSystemPromptList() {
        registry.register(new SkillDefinition("code-review", "代码评审", "步骤", "当用户要求代码评审时"));
        registry.register(new SkillDefinition("commit-msg", "提交信息规范", "规则", "当用户要求提交代码时"));

        List<String> systemPromptList = new ArrayList<>(List.of("基础提示"));
        hook.skillCatalogHook(new HookEvent(HookEventType.SESSION_START, "sess-1", systemPromptList));

        assertThat(systemPromptList).containsExactly(
                "基础提示",
                "【可用技能】需要时调用 invoke_skill(name) 激活：\n"
                        + "- code-review: 代码评审\n"
                        + "- commit-msg: 提交信息规范");
    }

    @Test
    void skipsWhenNoSkillsRegistered() {
        List<String> systemPromptList = new ArrayList<>(List.of("基础提示"));
        hook.skillCatalogHook(new HookEvent(HookEventType.SESSION_START, "sess-2", systemPromptList));
        assertThat(systemPromptList).containsExactly("基础提示");
    }

    @Test
    void doesNothingWhenPayloadNull() {
        hook.skillCatalogHook(new HookEvent(HookEventType.SESSION_START, "sess-3", null));
    }
}