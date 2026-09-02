package com.learn.mycc.tools;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.exception.MyccException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * bash 工具：执行 shell 命令并返回 stdout / stderr / 退出码。
 *
 * <p>职责边界：本工具只负责"执行命令并收集输出"，不做任何命令白名单/黑名单校验，
 * 因此命令注入风险由调用方（agent 生成的脚本）自行承担；本学习项目中默认信任 agent
 * 输入，仅做空值兜底。命令通过系统 shell 执行（Windows 用 {@code cmd /c}，其余用
 * {@code sh -c}），以兼容各平台的常用命令。</p>
 */
@Component
public final class BashTool {

    /**
     * 执行一条 shell 命令并返回其结果。
     *
     * @param command 要执行的 shell 命令，不能为 null 或空白；会被交给系统 shell 解析执行
     * @return stdout / stderr / exitCode 的执行结果，命令执行完必返回，结果字段可能为空串
     * @throws MyccException 命令为空白、启动进程失败（IOException）或被中断时抛出
     */
    @Tool(name = "bash", description = "执行 shell 命令，返回 stdout / stderr / 退出码")
    public BashResult bash(@ToolParam(description = "要执行的 shell 命令") String command) {
        if (command == null || command.isBlank()) {
            throw new MyccException("命令不能为空");
        }
        ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
        // redirectErrorStream(false) 让 stdout 与 stderr 保持独立流，从而在结果中分开返回
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            // 并发读两个流，避免某个流输出撑满管道缓冲、未被读取而阻塞进程写死锁
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            // 默认阻塞等待退出；未设超时，长时运行的命令会一直挂着，属已知边界
            int exitCode = process.waitFor();
            return new BashResult(stdout.join(), stderr.join(), exitCode);
        } catch (IOException e) {
            throw new MyccException("执行命令失败: " + command, e);
        } catch (InterruptedException e) {
            // 恢复中断标志，交由上层线程响应中断，避免吞掉中断状态
            Thread.currentThread().interrupt();
            throw new MyccException("执行命令被中断: " + command, e);
        }
    }

    /**
     * 根据当前操作系统选择对应的 shell 包装命令。
     *
     * @param command 原始命令字符串
     * @return 形如 {@code [cmd, /c, command]}（Windows）或 {@code [sh, -c, command]}（其余）的参数列表
     */
    private List<String> shellCommand(String command) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return windows ? List.of("cmd", "/c", command) : List.of("sh", "-c", command);
    }

    /**
     * 将进程的某个输出流全部读为 UTF-8 字符串。
     *
     * @param stream 进程的 stdout 或 stderr 输入流；由调用方持有并负责轮换读取
     * @return 流中全部字节按 UTF-8 解码后的字符串，可能为空串
     * @throws MyccException 读取流失败（IOException）时抛出
     */
    private String read(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("读取命令输出失败", e);
        }
    }
}
