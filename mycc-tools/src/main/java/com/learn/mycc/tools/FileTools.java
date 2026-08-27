package com.learn.mycc.tools;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.exception.MyccException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 文件读写编辑工具：read_file / write_file / edit_file，所有路径限定在工作区内。 */
@Component
public final class FileTools {

    private final WorkspacePaths paths;

    public FileTools(Path workspaceRoot) {
        this.paths = new WorkspacePaths(workspaceRoot);
    }

    /** 容器创建用：默认以当前目录为工作区。 */
    public FileTools() {
        this(Path.of("").toAbsolutePath());
    }

    @Tool(name = "read_file", description = "读取工作区内文件的内容")
    public String readFile(@ToolParam(description = "工作区内相对路径") String path) {
        return read(paths.resolve(path), path);
    }

    @Tool(name = "write_file", description = "写入文件内容（覆盖，父目录自动创建）")
    public String writeFile(@ToolParam(description = "工作区内相对路径") String path,
                            @ToolParam(description = "要写入的内容") String content) {
        Path resolved = paths.resolve(path);
        try {
            Path parent = resolved.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolved, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("写入文件失败: " + path, e);
        }
        return "已写入: " + path;
    }

    @Tool(name = "edit_file", description = "用新文本替换文件中首次出现的旧文本")
    public String editFile(@ToolParam(description = "工作区内相对路径") String path,
                           @ToolParam(description = "要被替换的旧文本") String oldString,
                           @ToolParam(description = "替换后的新文本") String newString) {
        Path resolved = paths.resolve(path);
        String content = read(resolved, path);
        if (oldString == null || !content.contains(oldString)) {
            throw new MyccException("未找到要替换的文本: " + oldString);
        }
        int index = content.indexOf(oldString);
        String updated = content.substring(0, index) + newString + content.substring(index + oldString.length());
        try {
            Files.writeString(resolved, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("写入文件失败: " + path, e);
        }
        return "已编辑: " + path;
    }

    private String read(Path resolved, String path) {
        if (!Files.isRegularFile(resolved)) {
            throw new MyccException("文件不存在或不是普通文件: " + path);
        }
        try {
            return Files.readString(resolved, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("读取文件失败: " + path, e);
        }
    }
}
