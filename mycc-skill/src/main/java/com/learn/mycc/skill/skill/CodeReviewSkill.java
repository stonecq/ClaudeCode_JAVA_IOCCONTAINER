package com.learn.mycc.skill.skill;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Skill;

/** 示例技能：代码评审。作为 {@code @Skill} + {@code @Component} 装配演示。 */
@Component
@Skill(name = "code-review",
        description = "代码评审",
        instructions = "按以下步骤评审代码：\n"
                + "1. 通读改动，掌握改动意图；\n"
                + "2. 依次检查正确性（是否引入 bug）、结构（职责与依赖是否合理）、可读性（命名/注释/重复）；\n"
                + "3. 输出结论与具体修改建议，指出文件与行号。",
        trigger = "当用户要求代码评审或 review 代码时")
public class CodeReviewSkill {
}