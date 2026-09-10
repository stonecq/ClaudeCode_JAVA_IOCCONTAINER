package com.learn.mycc.skill.file;

import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.skill.SkillDefinition;
import com.learn.mycc.core.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillFileLoaderTest {

    @TempDir
    Path tempDir;

    Path globalRoot;
    Path projectRoot;
    SkillRegistry registry;

    @BeforeEach
    void setUp() {
        globalRoot = tempDir.resolve("global").resolve("skills");
        projectRoot = tempDir.resolve("project").resolve("skills");
        registry = new SkillRegistry();
    }

    private void writeSkill(Path root, String name, String description) throws IOException {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), "---\ndescription: " + description + "\n---\n指令");
    }

    @Test
    void loadsGlobalAndProjectSkills() throws IOException {
        writeSkill(globalRoot, "code-review", "全局评审");
        writeSkill(projectRoot, "commit-msg", "提交规范");

        SkillFileLoader.load(registry, globalRoot, projectRoot);

        assertThat(registry.getAll()).extracting(SkillDefinition::getName)
                .containsExactlyInAnyOrder("code-review", "commit-msg");
        assertThat(registry.get("code-review").getDescription()).isEqualTo("全局评审");
        assertThat(registry.get("commit-msg").getInstructions()).isEqualTo("指令");
    }

    @Test
    void projectOverridesGlobalWithSameName() throws IOException {
        writeSkill(globalRoot, "code-review", "全局评审");
        writeSkill(projectRoot, "code-review", "项目评审");

        SkillFileLoader.load(registry, globalRoot, projectRoot);

        assertThat(registry.getAll()).hasSize(1);
        assertThat(registry.get("code-review").getDescription()).isEqualTo("项目评审");
    }

    @Test
    void ignoresDirectoriesWithoutSkillMd() throws IOException {
        Files.createDirectories(globalRoot.resolve("no-skill-file"));
        writeSkill(projectRoot, "real", "实技能");

        SkillFileLoader.load(registry, globalRoot, projectRoot);

        assertThat(registry.getAll()).extracting(SkillDefinition::getName).containsExactly("real");
    }

    @Test
    void missingRootsDoNotFail() {
        SkillFileLoader.load(registry, tempDir.resolve("nope-a"), tempDir.resolve("nope-b"));
        assertThat(registry.getAll()).isEmpty();
    }

    @Test
    void globalSkillConflictingWithExistingRegistrationThrows() throws IOException {
        // 模拟注解技能已注册（name 相同），全局文件再 register 同名应快速失败
        registry.register(new SkillDefinition("code-review", "注解版", "inst", ""));
        writeSkill(globalRoot, "code-review", "全局版");

        assertThatThrownBy(() -> SkillFileLoader.load(registry, globalRoot, tempDir.resolve("empty")))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("重复注册技能");
    }

    @Test
    void componentInitializationOnEmptyRootsDoesNotFail() {
        SkillFileLoader loader = new SkillFileLoader(registry, new ApplicationConfig(projectRoot));
        loader.afterPropertiesSet();
        // 全局（真实 user.home/.mycc/skills）与项目空根都不崩；技能列表可能含真实全局项，但不影响校验
        assertThat(registry.getAll()).doesNotContainNull();
    }
}