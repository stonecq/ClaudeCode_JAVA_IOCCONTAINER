package com.learn.mycc.core.skill;

import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.skill.fixture.CodeReviewSkillFixture;
import com.learn.mycc.core.skill.fixture.CommitMsgSkillFixture;
import com.learn.mycc.core.skill.fixture.DuplicateSkillA;
import com.learn.mycc.core.skill.fixture.DuplicateSkillB;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillRegistryTest {

    @Test
    void registersSkillClassesFromBeans() {
        BeanFactory factory = new BeanFactory();
        SkillRegistry registry = new SkillRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(CodeReviewSkillFixture.class));
        factory.getBean(CodeReviewSkillFixture.class);

        assertThat(registry.getAll()).hasSize(1);
        SkillDefinition def = registry.get("code-review");
        assertThat(def.getDescription()).isEqualTo("代码评审");
        assertThat(def.getInstructions()).isEqualTo("按规范评审代码：结构/正确性/可读性");
        assertThat(def.getTrigger()).isEqualTo("当用户要求代码评审时");
    }

    @Test
    void getAllKeepsRegistrationOrder() {
        BeanFactory factory = new BeanFactory();
        SkillRegistry registry = new SkillRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(CodeReviewSkillFixture.class));
        factory.register(BeanDefinition.from(CommitMsgSkillFixture.class));
        factory.getBean(CodeReviewSkillFixture.class);
        factory.getBean(CommitMsgSkillFixture.class);

        assertThat(registry.getAll()).extracting(SkillDefinition::getName)
                .containsExactly("code-review", "commit-msg");
    }

    @Test
    void containerExposesRegisteredSkills() {
        IocContainer container = IocContainer.create();
        container.register(BeanDefinition.from(CodeReviewSkillFixture.class));
        container.start();
        assertThat(container.getSkillRegistry().getAll()).hasSize(1);
        assertThat(container.getSkillRegistry().get("code-review")).isNotNull();
    }

    @Test
    void rejectsDuplicateSkillNameAcrossBeans() {
        BeanFactory factory = new BeanFactory();
        SkillRegistry registry = new SkillRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(BeanDefinition.from(DuplicateSkillA.class));
        factory.register(BeanDefinition.from(DuplicateSkillB.class));
        factory.getBean(DuplicateSkillA.class);
        assertThatThrownBy(() -> factory.getBean(DuplicateSkillB.class))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("重复注册技能");
    }

    @Test
    void rejectsBlankSkillNameOnRegister() {
        SkillRegistry registry = new SkillRegistry();
        assertThatThrownBy(() -> registry.register(new SkillDefinition("   ", "desc", "inst", "")))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("技能名称不能为空");
    }

    @Test
    void ignoresUnannotatedBeans() {
        SkillRegistry registry = new SkillRegistry();
        registry.postProcessAfterInitialization(new Object(), "plain");
        assertThat(registry.getAll()).isEmpty();
    }

    @Test
    void getThrowsForUnknownSkill() {
        SkillRegistry registry = new SkillRegistry();
        assertThatThrownBy(() -> registry.get("missing"))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("未注册技能");
    }

    @Test
    void overrideRegistersNewSkill() {
        SkillRegistry registry = new SkillRegistry();
        registry.override(new SkillDefinition("a", "A", "inst", ""));
        assertThat(registry.getAll()).hasSize(1);
        assertThat(registry.get("a").getDescription()).isEqualTo("A");
    }

    @Test
    void overrideReplacesExistingSkillWithSameName() {
        SkillRegistry registry = new SkillRegistry();
        registry.register(new SkillDefinition("a", "旧描述", "inst", ""));
        registry.override(new SkillDefinition("a", "新描述", "inst2", ""));
        assertThat(registry.getAll()).hasSize(1);
        assertThat(registry.get("a").getDescription()).isEqualTo("新描述");
        assertThat(registry.get("a").getInstructions()).isEqualTo("inst2");
    }

    @Test
    void overrideRejectsBlankName() {
        SkillRegistry registry = new SkillRegistry();
        assertThatThrownBy(() -> registry.override(new SkillDefinition("  ", "desc", "inst", "")))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("技能名称不能为空");
    }
}