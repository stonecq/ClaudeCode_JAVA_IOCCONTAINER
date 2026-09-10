package com.learn.mycc.core.skill.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Skill;

/** 正常技能 fixture：提交信息规范（用于验证注册顺序）。 */
@Skill(name = "commit-msg", description = "提交信息规范", instructions = "按 Conventional Commits 写提交信息", trigger = "当用户要求提交代码时")
@Component
public class CommitMsgSkillFixture {
}