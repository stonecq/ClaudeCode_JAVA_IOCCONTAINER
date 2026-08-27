package com.learn.mycc.tools;

import com.learn.mycc.core.exception.MyccException;

import java.nio.file.Path;

/** 工作区路径解析：拒绝绝对路径与越界路径，保证工具只访问工作区内文件。 */
final class WorkspacePaths {

    private final Path root;

    WorkspacePaths(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    Path root() {
        return root;
    }

    Path resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new MyccException("路径不能为空");
        }
        Path path = Path.of(rawPath);
        if (path.isAbsolute()) {
            throw new MyccException("不允许绝对路径: " + rawPath);
        }
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new MyccException("路径超出工作区: " + rawPath);
        }
        return resolved;
    }
}
