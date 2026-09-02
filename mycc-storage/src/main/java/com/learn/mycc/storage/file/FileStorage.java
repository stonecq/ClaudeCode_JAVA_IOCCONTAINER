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
 * 文件系统存储：key 映射为根目录下的相对路径（斜杠分隔），key 越界
 * （绝对路径 / ".."）一律拒绝。
 * 默认根目录为 {@code ~/.mycc/}。
 *
 * <p>设计思路：作为 {@link Storage} 的文件系统实现，把抽象 key（如
 * {@code session/<id>.json}）直接映射为根目录下的相对文件，无需元数据表即可
 * 持久化且文件可人工查看；每个 key 一个文件。安全上通过 {@link #resolve} 对
 * key 做越界校验，防止把存储文件写到根目录之外。</p>
 */
public final class FileStorage implements Storage {

    /** 存储根目录，已归一化为绝对路径；所有数据文件必须位于该目录下。 */
    private final Path root;

    /**
     * 以指定目录为存储根创建实现。
     *
     * @param root 存储根目录；toAbsolutePath+normalize 归一化为绝对路径
     */
    public FileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** @return 使用默认位置 {@code ~/.mycc/} 作为存储根的实现 */
    public static FileStorage defaultDirectory() {
        return new FileStorage(Path.of(System.getProperty("user.home"), ".mycc"));
    }

    /** @return 存储根目录（绝对路径） */
    public Path root() {
        return root;
    }

    /**
     * 写入或覆盖 key 对应的内容；父级目录不存在时自动创建。
     *
     * @param key     存储键（相对路径，斜杠分层），不能为空、不能越界
     * @param content 要持久化的内容，按 UTF-8 覆盖写
     * @throws MyccException key 非法或写入失败（IOException）时抛出
     */
    @Override
    public void write(String key, String content) {
        Path target = resolve(key);
        try {
            // key 可能含多级斜杠（如 a/b.json），先确保父目录存在，避免写入报目录不存在
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MyccException("写入存储失败: " + key + " / " + e.getMessage(), e);
        }
    }

    /**
     * 读取 key 对应的内容。
     *
     * @param key 存储键（相对路径），不能为空、不能越界
     * @return 存在且为普通文件时返回内容，否则返回 {@link Optional#empty()}（区别于抛异常）
     * @throws MyccException key 非法或读取失败（IOException）时抛出
     */
    @Override
    public Optional<String> read(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            // 不存在或不是普通文件都视为"无此 key"，返回 empty 让调用方走兜底逻辑
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MyccException("读取存储失败: " + key + " / " + e.getMessage(), e);
        }
    }

    /**
     * 删除 key 对应的内容；不存在返回 false，成功删除返回 true。
     *
     * @param key 存储键（相对路径），不能为空、不能越界
     * @return 存在并被删除返回 true；原本不存在返回 false
     * @throws MyccException key 非法或删除失败（IOException）时抛出
     */
    @Override
    public boolean delete(String key) {
        Path target = resolve(key);
        try {
            // deleteIfExists：文件不存在时不抛异常而返回 false，语义友好
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new MyccException("删除存储失败: " + key + " / " + e.getMessage(), e);
        }
    }

    /**
     * 列出所有已存在的 key（相对路径、按序、用斜杠分隔）。
     *
     * <p>实现说明：递归遍历根目录下所有普通文件，分离出相对路径并把平台分隔符
     * 统一替换为斜杠，还原成与写入选相同形态的 key；排序保证输出稳定可预期。</p>
     *
     * @return 全部 key 列表；根目录不存在或为空时返回空列表
     * @throws MyccException 遍历文件系统失败（IOException）时抛出
     */
    @Override
    public List<String> keys() {
        if (!Files.isDirectory(root)) {
            // 根目录尚不存在（首次使用）时无任何 key，直接返回空，避免 walk 抛错
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

    /**
     * 返回 key 对应内容最后修改时间的 epoch 毫秒；内容不存在返回 {@link Optional#empty()}。
     *
     * <p>用途：供上层判断数据是否过期/需刷新，比读取完整内容更轻量。</p>
     *
     * @param key 存储键（相对路径），不能为空、不能越界
     * @return 最后修改时间（毫秒）；键不存在或非普通文件时返回 {@link Optional#empty()}
     * @throws MyccException key 非法或读取修改时间失败（IOException）时抛出
     */
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

    /**
     * 把用户提供的 key 解析为根目录下的合法文件路径。
     *
     * <p>校验流程：① 拒绝 null/空白；② normalize 消除 {@code ..} / 冗余段后
     * 校验结果仍以 root 为前缀，拦截绝对路径与 {@code ..} 造成的目录穿越；
     * ③ 捕获 {@link InvalidPathException} 处理平台非法字符。</p>
     *
     * @param key 用户传入的存储键，不能为空、不能越界、不能含非法路径字符
     * @return 归一化且位于根目录内的绝对文件路径
     * @throws MyccException key 为空或非法路径时抛出
     */
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
