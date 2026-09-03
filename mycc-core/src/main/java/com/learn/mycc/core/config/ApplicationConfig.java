package com.learn.mycc.core.config;

import com.learn.mycc.core.annotation.Component;

import java.nio.file.Path;

@Component
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
