package com.learn.mycc.storage.file;

import com.learn.mycc.core.config.ApplicationConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 工作区存储：指向 {@code <workspace>/.mycc} 的 {@link FileStorage} 门面。
 * <p>复用 FileStorage 的 key→相对路径映射与越界保护，供技能目录、上下文转存等按 key
 * 读写，替代各处直接拼接 {@code <workspace>/.mycc} 路径。key 形如
 * {@code skills/<name>/SKILL.md}、{@code compact/tool-results/<id>.txt}。</p>
 * <p>刻意<b>不</b>实现 {@link com.learn.mycc.storage.spi.Storage}：容器按 Class 注册
 * （{@code BeanFactory.register} 类型唯一），再注册一个 {@code FileStorage}/{@code Storage}
 * 类型会与其冲突；用独立类型才能与全局存储并存。由 {@link com.learn.mycc.storage.config.StorageConfig}
 * 以 {@code @Bean} 提供。</p>
 */
public class WorkspaceStorage {

    private final FileStorage delegate;

    public WorkspaceStorage(ApplicationConfig config) {
        this.delegate = new FileStorage(config.getWorkspacePath().resolve(".mycc"));
    }

    /** 写入或覆盖 key 对应内容；父目录不存在自动创建。 */
    public void write(String key, String content) {
        delegate.write(key, content);
    }

    /** 读取 key 对应内容；不存在返回 {@link Optional#empty()}。 */
    public Optional<String> read(String key) {
        return delegate.read(key);
    }

    /** 删除 key；不存在返回 false。 */
    public boolean delete(String key) {
        return delegate.delete(key);
    }

    /** 列出全部 key（相对路径、斜杠分隔、按序）。 */
    public List<String> keys() {
        return delegate.keys();
    }

    /** @return 工作区 {@code .mycc} 根目录（绝对路径），供展示真实路径用。 */
    public Path root() {
        return delegate.root();
    }
}