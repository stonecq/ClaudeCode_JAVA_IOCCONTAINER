package com.learn.mycc.core.config;

import java.nio.file.Path;

/**
 * 应用运行时配置：当前承载工作区根路径。由 {@code StorageConfig} 的 {@code @Bean} 依据
 * 配置项 {@code workpath} 装配（core 不依赖 storage，故不在此注入配置服务）。
 */
public class ApplicationConfig {

    private final Path workspacePath;

    public ApplicationConfig() {
        this(Path.of("").toAbsolutePath());
    }

    public ApplicationConfig(Path workspacePath) {
        this.workspacePath = workspacePath.toAbsolutePath().normalize();
    }

    public Path getWorkspacePath(){
        return workspacePath;
    }

}
