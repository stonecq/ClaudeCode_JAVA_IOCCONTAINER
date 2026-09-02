package com.learn.mycc.tools;

import com.learn.mycc.core.exception.MyccException;

import java.nio.file.Path;

/**
 * 工作区路径解析：拒绝绝对路径与越界路径，保证工具只访问工作区内文件。
 *
 * <p>设计思路：所有工具（FileTools / SearchTools）共享同一个路径校验逻辑，
 * 抽成此类可避免各工具重复实现并保证校验策略一致。核心手段是 normalize
 * 后校验结果是否仍以工作区根为前缀，从而拦截 {@code ..} 与绝对路径造成的
 * 目录穿越（Path Traversal）。包级可见，仅供本工具包内部使用。</p>
 *
 * <p>安全注意：此类是工具可访问任意文件系统的安全边界，任何出入此类的
 * 用户输入都必须先经过 {@link #resolve} 校验。</p>
 */
final class WorkspacePaths {

    /** 工作区根目录，已归一化为绝对路径；解析出的所有路径必须以此为前缀。 */
    private final Path root;

    /**
     * 以指定目录为工作区根，并归一化为绝对路径。
     *
     * @param root 工作区根；toAbsolutePath+normalize 消除相对表示与冗余路径段
     */
    WorkspacePaths(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** @return 工作区根目录（绝对路径） */
    Path root() {
        return root;
    }

    /**
     * 将用户输入相对路径解析为合法的绝对路径。
     *
     * <p>校验流程：① 拒绝空值/空白；② 拒绝绝对路径（强制要求相对路径输入）；
     * ③ normalize 消除 {@code ..} / {@code .} / 冗余分隔符后，校验结果必须仍
     * 以 root 为前缀，否则说明越出工作区。</p>
     *
     * @param rawPath 用户输入的相对路径，不能为 null、空白、绝对路径，且不得越出工作区
     * @return 归一化且被确认位于工作区内的绝对路径
     * @throws MyccException 输入为空/绝对路径/越界时抛出
     */
    Path resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new MyccException("路径不能为空");
        }
        Path path = Path.of(rawPath);
        if (path.isAbsolute()) {
            throw new MyccException("不允许绝对路径: " + rawPath);
        }
        Path resolved = root.resolve(path).normalize();
        // startsWith 判断路径前缀；normalize 已展开 ".."，此处即可识破穿越尝试
        if (!resolved.startsWith(root)) {
            throw new MyccException("路径超出工作区: " + rawPath);
        }
        return resolved;
    }
}
