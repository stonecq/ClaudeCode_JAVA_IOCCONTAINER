package com.learn.mycc.skill.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillFileParserTest {

    @TempDir
    Path tempDir;

    private Path writeSkill(String dirName, String content) throws IOException {
        Path dir = tempDir.resolve(dirName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), content);
        return dir;
    }

    @Test
    void parsesFullFrontmatterAndBody() throws Exception {
        Path dir = writeSkill("code-review", """
                ---
                description: 代码评审
                trigger: 当用户要求代码评审时
                ---
                按步骤评审：
                1. 通读改动""");

        SkillFileParser.ParsedSkill skill = SkillFileParser.parse(dir);
        assertThat(skill.name()).isEqualTo("code-review");
        assertThat(skill.description()).isEqualTo("代码评审");
        assertThat(skill.trigger()).isEqualTo("当用户要求代码评审时");
        assertThat(skill.instructions()).isEqualTo("按步骤评审：\n1. 通读改动");
    }

    @Test
    void missingTriggerDefaultsToEmpty() throws Exception {
        Path dir = writeSkill("commit-msg", """
                ---
                description: 提交信息规范
                ---
                按 Conventional Commits 写提交信息""");

        SkillFileParser.ParsedSkill skill = SkillFileParser.parse(dir);
        assertThat(skill.description()).isEqualTo("提交信息规范");
        assertThat(skill.trigger()).isEmpty();
        assertThat(skill.instructions()).isEqualTo("按 Conventional Commits 写提交信息");
    }

    @Test
    void noFrontmatterTreatsWholeFileAsInstructions() throws Exception {
        Path dir = writeSkill("plain", "直接一段指令\n第二行");

        SkillFileParser.ParsedSkill skill = SkillFileParser.parse(dir);
        assertThat(skill.description()).isEmpty();
        assertThat(skill.trigger()).isEmpty();
        assertThat(skill.instructions()).isEqualTo("直接一段指令\n第二行");
    }

    @Test
    void unclosedFrontmatterTreatsWholeFileAsInstructions() throws Exception {
        Path dir = writeSkill("broken", "---\ndescription: 未闭合\n正文");

        SkillFileParser.ParsedSkill skill = SkillFileParser.parse(dir);
        assertThat(skill.description()).isEmpty();
        assertThat(skill.instructions()).isEqualTo("---\ndescription: 未闭合\n正文");
    }

    @Test
    void ignoredUnknownFrontmatterKeys() throws Exception {
        Path dir = writeSkill("meta", "---\ndescription: 描述\nversion: 2\n---\n正文");

        SkillFileParser.ParsedSkill skill = SkillFileParser.parse(dir);
        assertThat(skill.name()).isEqualTo("meta");
        assertThat(skill.description()).isEqualTo("描述");
        assertThat(skill.instructions()).isEqualTo("正文");
    }
}