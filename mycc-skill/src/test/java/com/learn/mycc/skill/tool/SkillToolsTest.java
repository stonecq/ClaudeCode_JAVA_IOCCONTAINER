package com.learn.mycc.skill.tool;

import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.skill.SkillDefinition;
import com.learn.mycc.core.skill.SkillRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillToolsTest {

    @Test
    void invokeReturnsSkillInstructions() {
        SkillRegistry registry = new SkillRegistry();
        registry.register(new SkillDefinition("code-review", "代码评审", "按步骤评审代码", ""));
        SkillTools tools = new SkillTools(registry);

        assertThat(tools.invokeSkill("code-review")).isEqualTo("按步骤评审代码");
    }

    @Test
    void invokeUnknownSkillThrows() {
        SkillTools tools = new SkillTools(new SkillRegistry());
        assertThatThrownBy(() -> tools.invokeSkill("missing"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("未注册技能");
    }
}