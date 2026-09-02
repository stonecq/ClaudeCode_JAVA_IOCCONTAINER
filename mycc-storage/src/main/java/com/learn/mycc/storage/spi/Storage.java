package com.learn.mycc.storage.spi;

import java.util.List;
import java.util.Optional;

/**
 * 存储抽象：按 key 读写字符串内容。实现可对接文件系统、数据库、远程等。
 * key 建议用斜杠分层（如 {@code session/<id>.json}），具体规则由实现校验。
 */
public interface Storage {

    /** 写入或覆盖 key 对应的内容；父级目录不存在时自动创建。 */
    void write(String key, String content);

    /** 读取 key 对应的内容；不存在时返回 {@link Optional#empty()}。 */
    Optional<String> read(String key);

    /** 删除 key 对应的内容；不存在返回 false，成功删除返回 true。 */
    boolean delete(String key);

    /** 列出所有已存在的 key（相对路径、按序）。 */
    List<String> keys();

    /** 返回 key 对应内容最后修改时间的 epoch 毫秒；内容不存在返回 {@link Optional#empty()}。 */
    Optional<Long> lastModified(String key);
}
