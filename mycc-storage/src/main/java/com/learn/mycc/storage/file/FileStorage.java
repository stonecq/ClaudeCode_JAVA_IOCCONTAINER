package com.learn.mycc.storage.file;

import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.storage.spi.Storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 文件系统存储：key 映射为根目录下的相对路径（斜杠分隔），key 越界（绝对路径 / ".."）一律拒绝。
 * 默认根目录为 {@code ~/.mycc/}。
 */
public final class FileStorage implements Storage {

    private final Path root;

    public FileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public static FileStorage defaultDirectory() {
        return new FileStorage(Path.of(System.getProperty("user.home"), ".mycc"));
    }

    public Path root() {
        return root;
    }

    @Override
    public void write(String key, String content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("写入存储失败: " + key + " / " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> read(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MyccException("读取存储失败: " + key + " / " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String key) {
        Path target = resolve(key);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new MyccException("删除存储失败: " + key + " / " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> keys() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(path -> path.toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new MyccException("列出存储文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Long> lastModified(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.getLastModifiedTime(target).toMillis());
        } catch (IOException e) {
            throw new MyccException("读取存储修改时间失败: " + key + " / " + e.getMessage(), e);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new MyccException("存储 key 不能为空");
        }
        try {
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) {
                throw new MyccException("非法存储路径: " + key);
            }
            return target;
        } catch (InvalidPathException e) {
            throw new MyccException("非法存储路径: " + key, e);
        }
    }
}
