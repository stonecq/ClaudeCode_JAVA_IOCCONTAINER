package com.learn.mycc.core.skill.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Skill;

/** 正常技能 fixture：代码评审。 */
@Skill(name = "code-review", description = "代码评审", instructions = "按规范评审代码：结构/正确性/可读性", trigger = "当用户要求代码评审时")
@Component
public class CodeReviewSkillFixture {
}