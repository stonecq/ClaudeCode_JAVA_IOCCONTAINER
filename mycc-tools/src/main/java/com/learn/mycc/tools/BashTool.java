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

/** bash 工具：执行 shell 命令并返回 stdout / stderr / 退出码。 */
@Component
public final class BashTool {

    @Tool(name = "bash", description = "执行 shell 命令，返回 stdout / stderr / 退出码")
    public BashResult bash(@ToolParam(description = "要执行的 shell 命令") String command) {
        if (command == null || command.isBlank()) {
            throw new MyccException("命令不能为空");
        }
        ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            // 并发读两个流，避免输出超过管道缓冲导致死锁
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            int exitCode = process.waitFor();
            return new BashResult(stdout.join(), stderr.join(), exitCode);
        } catch (IOException e) {
            throw new MyccException("执行命令失败: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MyccException("执行命令被中断: " + command, e);
        }
    }

    private List<String> shellCommand(String command) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return windows ? List.of("cmd", "/c", command) : List.of("sh", "-c", command);
    }

    private String read(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("读取命令输出失败", e);
        }
    }
}
