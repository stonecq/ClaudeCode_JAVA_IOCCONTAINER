package com.learn.mycc.skill.file;

import com.learn.mycc.core.exception.MyccException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SKILL.md 解析：从「技能目录/SKILL.md」解析出 {@link ParsedSkill}。
 * 技能名取目录名（确定性强，避免 frontmatter 与目录不一致）；
 * 元数据为文件头两个 {@code ---} 包裹的 {@code key: value} 行（description/trigger，
 * 其它键忽略），正文为闭合标记之后的全部内容。仅支持简单键值 frontmatter，
 * 零 YAML 依赖。无成对 {@code ---} 时按「全文即指令」处理（宽容加载）。
 */
final class SkillFileParser {

    /** frontmatter 起止标记（首尾各一行）。 */
    private static final String FRONTMATTER_MARK = "---";

    /** SKILL.md 文件名常量，供加载器识别技能目录使用。 */
    static final String SKILL_FILE = "SKILL.md";

    /** 解析结果：名称 + 元数据（description/trigger）+ 指令正文。 */
    record ParsedSkill(String name, String description, String instructions, String trigger) {
    }

    private SkillFileParser() {
    }

    /**
     * 解析一个技能目录。
     *
     * @param skillDir 含 SKILL.md 的技能目录；目录名即技能名
     * @return 解析结果
     * @throws MyccException 文件不存在或读取失败时抛出
     */
    static ParsedSkill parse(Path skillDir) {
        Path file = skillDir.resolve(SKILL_FILE);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return parse(skillDir.getFileName().toString(), text);
        } catch (IOException e) {
            throw new MyccException("读取技能文件失败: " + file, e);
        }
    }

    /**
     * 解析技能文本（供已从存储读入内容的加载器直接使用）。
     *
     * @param name 技能名（由加载器从存储 key 段得出，如 {@code skills/<name>/SKILL.md} 的 name）
     * @param text SKILL.md 全文
     * @return 解析结果
     */
    public static ParsedSkill parse(String name, String text) {
        List<String> lines = text.lines().toList();
        int closeIdx = frontmatterEnd(lines);
        if (closeIdx < 0) {
            return new ParsedSkill(name, "", text.strip(), "");
        }
        String description = "";
        String trigger = "";
        for (int i = 1; i < closeIdx; i++) {
            String[] kv = lines.get(i).split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            switch (key) {
                case "description" -> description = value;
                case "trigger" -> trigger = value;
                default -> {
                    // 未知键忽略，兼容更丰富的元数据
                }
            }
        }
        String body = body(lines, closeIdx);
        return new ParsedSkill(name, description, body, trigger);
    }

    /**
     * 若首行为 {@code ---} 且后续存在第二行 {@code ---}，返回闭合行下标；
     * 否则返回 -1（无成对 frontmatter，按全文即正文处理）。
     */
    private static int frontmatterEnd(List<String> lines) {
        if (lines.isEmpty() || !lines.get(0).trim().equals(FRONTMATTER_MARK)) {
            return -1;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(FRONTMATTER_MARK)) {
                return i;
            }
        }
        return -1;
    }

    /** 取闭合行之后的正文，strip 首尾空白。 */
    private static String body(List<String> lines, int closeIdx) {
        List<String> body = new ArrayList<>(lines.subList(closeIdx + 1, lines.size()));
        return String.join("\n", body).strip();
    }
}