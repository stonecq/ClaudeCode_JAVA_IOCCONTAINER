package com.learn.mycc.tools;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 搜索工具：glob / grep / search_files，遍历范围限定在工作区内。
 *
 * <p>职责边界：提供三种检索能力，分别对应按命名模式找文件、按正则匹配行、
 * 按关键字子串找文件；所有遍历起始根都以工作区（{@link ApplicationConfig#getWorkspacePath()}
 * 解析，越界路径由 tool_call_before 钩子拦截，工具内部不再重复校验。返回的空结果
 * 统一以 {@link #NO_MATCH} 表示，方便 agent 直接区分"有/无"而无需解析空串。</p>
 */
@Component
public final class SearchTools {
    private final ApplicationConfig config;

    public SearchTools(ApplicationConfig config) {
        this.config = config;
    }

    /** 无匹配结果时对外返回的占位文案，避免返回空串让 agent 误判为"指令未执行"。 */
    private static final String NO_MATCH = "无匹配结果";
    /**
     * 按 glob 模式遍历工作区，列出所有匹配的普通文件（相对路径、排序、斜杠分隔）。
     *
     * <p>实现说明：抽象出 {@link PathMatcher} 复用 JDK 内置的 glob 语法，支持
     * {@code **} 递归匹配，避免自研解析；用 try-with-resources 关闭 {@link Files#walk}
     * 的底层目录流以防句柄泄漏。</p>
     *
     * @param pattern glob 模式，如 {@code **\/*.java}，不能为 null 或空白
     * @return 匹配文件的相对路径，每行一个；无匹配返回 {@link #NO_MATCH}
     * @throws MyccException 模式为空或遍历文件系统失败（IOException）时抛出
     */
    @Tool(name = "glob", description = "按 glob 模式列出工作区内匹配的文件（相对路径）")
    public String glob(@ToolParam(description = "glob 模式，如 **/*.java") String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new MyccException("glob 模式不能为空");
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<String> found = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(config.getWorkspacePath())) {
            stream.filter(Files::isRegularFile)
                    .map(config.getWorkspacePath()::relativize)
                    .filter(matcher::matches)
                    .map(SearchTools::toSlashPath)
                    .sorted()
                    .forEach(found::add);
        } catch (IOException e) {
            throw new MyccException("glob 搜索失败: " + pattern, e);
        }
        return found.isEmpty() ? NO_MATCH : String.join("\n", found);
    }

    /**
     * 按正则表达式在工作区的文件（或指定子树）中匹配行，返回
     * {@code 相对路径:行号: 行内容}。
     *
     * <p>边界说明：正则由 {@link Pattern} 直接编译，不设复制度上限，理论上
     * 可被灾难性回溯的正则拖慢；无超时保护属已知边界，后续可加开关。</p>
     *
     * @param regex 正则表达式，不能为 null 或空白
     * @param path  相对路径（文件或目录），为 null/空白时回退到整个工作区
     * @return 匹配行列表，每行一条；无匹配返回 {@link #NO_MATCH}
     * @throws MyccException 正则为空、路径不存在或读取/遍历文件失败时抛出
     */
    @Tool(name = "grep", description = "按正则表达式在文件中匹配行，返回 相对路径:行号: 行内容")
    public String grep(@ToolParam(description = "正则表达式") String regex,
                       @ToolParam(description = "相对路径（文件或目录，默认整个工作区）", required = false) String path) {
        if (regex == null || regex.isBlank()) {
            throw new MyccException("正则表达式不能为空");
        }
        Pattern pattern = Pattern.compile(regex);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(searchRoot(path))) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> matches.addAll(matchLines(file, pattern)));
        } catch (IOException e) {
            throw new MyccException("grep 搜索失败", e);
        }
        return matches.isEmpty() ? NO_MATCH : String.join("\n", matches);
    }

    /**
     * 按关键字（子串包含匹配）查找内容含该关键字的文件，返回相对路径列表。
     *
     * <p>与 grep 的区别：search_files 不关心匹配行号，仅判断文件内容是否包含
     * 该子串，输出的是文件路径而非命中行，适合"找出涉及某主题的文件"。</p>
     *
     * @param query 搜索关键字（子串），不能为 null 或空白
     * @param path  相对路径（文件或目录），为 null/空白时回退到整个工作区
     * @return 内容含关键字的文件的相对路径，每行一个；无匹配返回 {@link #NO_MATCH}
     * @throws MyccException 关键字为空、路径不存在或读取/遍历文件失败时抛出
     */
    @Tool(name = "search_files", description = "按关键字（子串）查找包含内容的文件，返回相对路径")
    public String searchFiles(@ToolParam(description = "搜索关键字") String query,
                              @ToolParam(description = "相对路径（文件或目录，默认整个工作区）", required = false) String path) {
        if (query == null || query.isBlank()) {
            throw new MyccException("关键字不能为空");
        }
        List<String> found = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(searchRoot(path))) {
            stream.filter(Files::isRegularFile)
                    .filter(file -> fileContains(file, query))
                    .map(config.getWorkspacePath()::relativize)
                    .map(SearchTools::toSlashPath)
                    .sorted()
                    .forEach(found::add);
        } catch (IOException e) {
            throw new MyccException("search_files 失败", e);
        }
        return found.isEmpty() ? NO_MATCH : String.join("\n", found);
    }

    /**
     * 确定搜索遍历的起始根：未指定时用整个工作区，指定时先校验再定位。
     *
     * @param path 用户指定的相对路径；null/空白表示整个工作区
     * @return 遍历起始目录或文件路径（已解析、绝对）
     * @throws MyccException 指定路径不存在时抛出
     */
    private Path searchRoot(String path) {
        if (path == null || path.isBlank()) {
            return config.getWorkspacePath();
        }
        Path resolved = config.getWorkspacePath().resolve(path);
        if (!Files.exists(resolved)) {
            throw new MyccException("路径不存在: " + path);
        }
        return resolved;
    }

    /**
     * 单文件正则匹配：逐行判断是否命中，命中则组装成
     * {@code 相对路径:行号:行内容} 输出。
     *
     * <p>实现说明：列出所有行后带行号遍历，行号从 1 开始，与常见编辑器的
     * 显示一致，方便 agent 定位。</p>
     *
     * @param file    要匹配的文件
     * @param pattern 已编译的正则模式
     * @return 命中行格式化后的列表，无命中则返回空列表
     * @throws MyccException 文件读取失败（IOException）时抛出
     */
    private List<String> matchLines(Path file, Pattern pattern) {
        List<String> lines = new ArrayList<>();
        try {
            List<String> allLines = Files.readAllLines(file);
            for (int i = 0; i < allLines.size(); i++) {
                // 用 find() 而非 matches()：只要行中任一部分命中即算命中，符合 grep 语义
                if (pattern.matcher(allLines.get(i)).find()) {
                    lines.add(toSlashPath(config.getWorkspacePath().relativize(file)) + ":" + (i + 1) + ": " + allLines.get(i));
                }
            }
        } catch (IOException e) {
            throw new MyccException("读取文件失败: " + file, e);
        }
        return lines;
    }

    /**
     * 判断单个文件内容是否包含指定子串。
     *
     * @param file  要检查的文件
     * @param query 搜索关键字（子串）
     * @return 内容包含关键字返回 true，否则 false
     * @throws MyccException 文件读取失败（IOException）时抛出
     */
    private boolean fileContains(Path file, String query) {
        try {
            return Files.readString(file).contains(query);
        } catch (IOException e) {
            throw new MyccException("读取文件失败: " + file, e);
        }
    }

    /**
     * 把平台路径分隔符统一替换为斜杠，使输出在 Windows 与 Unix 下保持一致。
     *
     * @param path 待转换的路径
     * @return 使用正斜杠分隔的路径字符串
     */
    private static String toSlashPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
