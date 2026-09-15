package com.learn.mycc.skill.file;

import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.skill.SkillDefinition;
import com.learn.mycc.core.skill.SkillRegistry;
import com.learn.mycc.storage.file.FileStorage;
import com.learn.mycc.storage.file.WorkspaceStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillFileLoaderTest {

    @TempDir
    Path tempDir;

    FileStorage global;
    WorkspaceStorage workspace;
    SkillRegistry registry;

    @BeforeEach
    void setUp() {
        global = new FileStorage(tempDir.resolve("global"));
        workspace = new WorkspaceStorage(new ApplicationConfig(tempDir.resolve("project")));
        registry = new SkillRegistry();
    }

    private void writeGlobal(String name, String description) {
        global.write("skills/" + name + "/SKILL.md", "---\ndescription: " + description + "\n---\n指令");
    }

    private void writeWorkspace(String name, String description) {
        workspace.write("skills/" + name + "/SKILL.md", "---\ndescription: " + description + "\n---\n指令");
    }

    private SkillFileLoader loader() {
        return new SkillFileLoader(registry, global, workspace);
    }

    @Test
    void loadsGlobalAndProjectSkills() {
        writeGlobal("code-review", "全局评审");
        writeWorkspace("commit-msg", "提交规范");

        loader().afterPropertiesSet();

        assertThat(registry.getAll()).extracting(SkillDefinition::getName)
                .containsExactlyInAnyOrder("code-review", "commit-msg");
        assertThat(registry.get("code-review").getDescription()).isEqualTo("全局评审");
        assertThat(registry.get("commit-msg").getInstructions()).isEqualTo("指令");
    }

    @Test
    void projectOverridesGlobalWithSameName() {
        writeGlobal("code-review", "全局评审");
        writeWorkspace("code-review", "项目评审");

        loader().afterPropertiesSet();

        assertThat(registry.getAll()).hasSize(1);
        assertThat(registry.get("code-review").getDescription()).isEqualTo("项目评审");
    }

    @Test
    void ignoresKeysNotMatchingSkillFile() {
        global.write("skills/notes/README.md", "x");
        writeWorkspace("real", "真技能");

        loader().afterPropertiesSet();

        assertThat(registry.getAll()).extracting(SkillDefinition::getName).containsExactly("real");
    }

    @Test
    void emptyStoragesDoNotFail() {
        loader().afterPropertiesSet();
        assertThat(registry.getAll()).isEmpty();
    }

    @Test
    void globalSkillConflictingWithExistingRegistrationThrows() {
        registry.register(new SkillDefinition("code-review", "注解版", "inst", ""));
        writeGlobal("code-review", "全局版");

        assertThatThrownBy(() -> loader().afterPropertiesSet())
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("重复注册技能");
    }
}