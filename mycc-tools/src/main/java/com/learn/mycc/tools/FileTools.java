package com.learn.mycc.tools;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.annotation.ToolRisk;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件读写编辑工具：read_file / write_file / edit_file，所有路径相对于工作区根解析。
 *
 * <p>路径解析基准为 {@link ApplicationConfig#getWorkspacePath()}，保证读写落在工作区内；
 * 越界/绝对路径由 tool_call_before 钩子拦截，工具内部不重复校验。文件内容统一按
 * UTF-8 读写，保证跨平台一致。</p>
 */
@Component
public final class FileTools {

    @Inject
    private ApplicationConfig config;

    public FileTools() {}

    public FileTools(ApplicationConfig config) {
        this.config = config;
    }

    /**
     * 读取工作区内文件的内容。
     *
     * @param path 工作区内相对路径（不能为空、不能越界）
     * @return 文件的 UTF-8 文本内容
     * @throws MyccException 路径非法、文件不存在或不是普通文件、读取失败时抛出
     */
    @Tool(name = "read_file", description = "读取工作区内文件的内容")
    public String readFile(@ToolParam(description = "工作区内相对路径") String path) {
        return read(config.getWorkspacePath().resolve(path));
    }

    /**
     * 写入文件内容（覆盖写，父目录自动创建）。
     *
     * @param path    工作区内相对路径（不能为空、不能越界）
     * @param content 要写入的内容，按 UTF-8 覆盖原文件
     * @return 固定提示字符串，形如 {@code "已写入: <path>"}
     * @throws MyccException 路径非法或写入失败（IOException）时抛出
     */
    @Tool(name = "write_file", description = "写入文件内容（覆盖，父目录自动创建）", risk = ToolRisk.HIGH)
    public String writeFile(@ToolParam(description = "工作区内相对路径") String path,
                            @ToolParam(description = "要写入的内容") String content) {
        Path filePath = config.getWorkspacePath().resolve(path);
        try {
            // 父目录可能不存在（嵌套路径），先递归创建，避免写入时目录不存在而失败
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("写入文件失败: " + path, e);
        }
        return "已写入: " + path;
    }

    /**
     * 用新文本替换文件中首次出现的旧文本，覆盖写回。
     *
     * @param path      工作区内相对路径（不能为空、不能越界）
     * @param oldString 要被替换的旧文本，不能为 null；若在文件中不存在则抛异常
     * @param newString 替换后的新文本
     * @return 固定提示字符串，形如 {@code "已编辑: <path>"}
     * @throws MyccException 路径非法、未找到旧文本或读写文件失败时抛出
     */
    @Tool(name = "edit_file", description = "用新文本替换文件中首次出现的旧文本", risk = ToolRisk.HIGH)
    public String editFile(@ToolParam(description = "工作区内相对路径") String path,
                           @ToolParam(description = "要被替换的旧文本") String oldString,
                           @ToolParam(description = "替换后的新文本") String newString) {
        Path filePath = config.getWorkspacePath().resolve(path);
        String content = read(filePath);
        if (oldString == null || !content.contains(oldString)) {
            throw new MyccException("未找到要替换的文本: " + oldString);
        }
        // 只替换首次出现：定位其起始下标，前后切片夹入新文本，避免全局误替换
        int index = content.indexOf(oldString);
        String updated = content.substring(0, index) + newString + content.substring(index + oldString.length());
        try {
            Files.writeString(filePath, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("写入文件失败: " + path, e);
        }
        return "已编辑: " + path;
    }

    /**
     * 读取单个普通文件的内容（UTF-8）。
     *
     * @param path     用户原始输入路径，仅用于错误提示
     * @return 文件的 UTF-8 文本内容
     * @throws MyccException 目标不是普通文件（目录或不存在）或读取失败时抛出
     */
    private String read(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new MyccException("文件不存在或不是普通文件: " + path);
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("读取文件失败: " + path, e);
        }
    }
}
