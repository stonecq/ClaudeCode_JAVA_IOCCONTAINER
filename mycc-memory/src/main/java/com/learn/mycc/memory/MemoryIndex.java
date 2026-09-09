package com.learn.mycc.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆索引的纯字符串操作：每层索引文件为「- &lt;id&gt;: &lt;description&gt;」每行一条。
 * 提供新增/更新（同 id 替换描述、保持位置）与移除，均为无副作用的文本变换，可独立测试；
 * 落盘由 {@link MemoryStorage} 负责。id/description 约定为单行，contains 换行的输入应被上层拒绝。
 */
final class MemoryIndex {

    /** 行前缀；解析与格式化共用同一常量保证一致。 */
    private static final String LINE_PREFIX = "- ";

    private MemoryIndex() {
    }

    /** 新增或更新：同 id 已存在则替换该行（更新描述，保持位置），否则追加到末尾。 */
    static String upsert(String indexText, String id, String description) {
        String line = formatLine(id, description);
        List<String> lines = lines(indexText);
        for (int i = 0; i < lines.size(); i++) {
            if (idOf(lines.get(i)).equals(id)) {
                lines.set(i, line);
                return String.join("\n", lines);
            }
        }
        lines.add(line);
        return String.join("\n", lines);
    }

    /** 移除指定 id 的行；全部移除后返回空串（调用方据此删除索引文件）。 */
    static String remove(String indexText, String id) {
        List<String> lines = lines(indexText);
        List<String> kept = lines.stream()
                .filter(line -> !idOf(line).equals(id))
                .toList();
        return String.join("\n", kept);
    }

    /** 把 id 与描述格式化为一条索引行。 */
    static String formatLine(String id, String description) {
        return LINE_PREFIX + id + ": " + description;
    }

    /** 解析一行文本中的 id；非法行（非「- 」开头或缺分隔符）返回空串，不匹配任何条目。 */
    private static String idOf(String line) {
        if (line == null || !line.startsWith(LINE_PREFIX)) {
            return "";
        }
        int colon = line.indexOf(": ");
        if (colon < LINE_PREFIX.length()) {
            return "";
        }
        return line.substring(LINE_PREFIX.length(), colon);
    }

    private static List<String> lines(String indexText) {
        if (indexText == null || indexText.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(indexText.lines().toList());
    }
}