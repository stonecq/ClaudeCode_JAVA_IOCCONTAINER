package com.learn.mycc.core.skill.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Skill;

/** 与 {@link DuplicateSkillB} 同名的技能类，用于重名校验。 */
@Skill(name = "dup", description = "A", instructions = "A", trigger = "")
@Component
public class DuplicateSkillA {
}