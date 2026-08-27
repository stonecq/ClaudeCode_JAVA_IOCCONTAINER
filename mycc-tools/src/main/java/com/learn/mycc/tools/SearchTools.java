package com.learn.mycc.tools;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
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

/** 搜索工具：glob / grep / search_files，遍历范围限定在工作区内。 */
@Component
public final class SearchTools {

    private static final String NO_MATCH = "无匹配结果";

    private final WorkspacePaths paths;

    public SearchTools(Path workspaceRoot) {
        this.paths = new WorkspacePaths(workspaceRoot);
    }

    /** 容器创建用：默认以当前目录为工作区。 */
    public SearchTools() {
        this(Path.of("").toAbsolutePath());
    }

    @Tool(name = "glob", description = "按 glob 模式列出工作区内匹配的文件（相对路径）")
    public String glob(@ToolParam(description = "glob 模式，如 **/*.java") String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new MyccException("glob 模式不能为空");
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<String> found = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(paths.root())) {
            stream.filter(Files::isRegularFile)
                    .map(paths.root()::relativize)
                    .filter(matcher::matches)
                    .map(SearchTools::toSlashPath)
                    .sorted()
                    .forEach(found::add);
        } catch (IOException e) {
            throw new MyccException("glob 搜索失败: " + pattern, e);
        }
        return found.isEmpty() ? NO_MATCH : String.join("\n", found);
    }

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
                    .map(paths.root()::relativize)
                    .map(SearchTools::toSlashPath)
                    .sorted()
                    .forEach(found::add);
        } catch (IOException e) {
            throw new MyccException("search_files 失败", e);
        }
        return found.isEmpty() ? NO_MATCH : String.join("\n", found);
    }

    private Path searchRoot(String path) {
        if (path == null || path.isBlank()) {
            return paths.root();
        }
        Path resolved = paths.resolve(path);
        if (!Files.exists(resolved)) {
            throw new MyccException("路径不存在: " + path);
        }
        return resolved;
    }

    private List<String> matchLines(Path file, Pattern pattern) {
        List<String> lines = new ArrayList<>();
        try {
            List<String> allLines = Files.readAllLines(file);
            for (int i = 0; i < allLines.size(); i++) {
                if (pattern.matcher(allLines.get(i)).find()) {
                    lines.add(toSlashPath(paths.root().relativize(file)) + ":" + (i + 1) + ": " + allLines.get(i));
                }
            }
        } catch (IOException e) {
            throw new MyccException("读取文件失败: " + file, e);
        }
        return lines;
    }

    private boolean fileContains(Path file, String query) {
        try {
            return Files.readString(file).contains(query);
        } catch (IOException e) {
            throw new MyccException("读取文件失败: " + file, e);
        }
    }

    private static String toSlashPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
