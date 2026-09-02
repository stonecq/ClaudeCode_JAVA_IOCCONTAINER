package com.learn.mycc.storage.spi;

import java.util.List;
import java.util.Optional;

/**
 * 存储抽象：按 key 读写字符串内容。实现可对接文件系统、数据库、远程等。
 * key 建议用斜杠分层（如 {@code session/<id>.json}），具体规则由实现校验。
 *
 * <p>设计思路：采用面向接口编程，把"存储能力"抽象为 SPI（服务提供方），使
 * agent/会话层的持久化逻辑只依赖本接口而不绑定具体实现，便于后续替换为数据库
 * 或远程存储。语义约定：内容一律为字符串，不存在与"内容为空"通过
 * {@link Optional} 区分。</p>
 */
public interface Storage {

    /**
     * 写入或覆盖 key 对应的内容；父级目录不存在时自动创建。
     *
     * @param key     存储键（建议斜杠分层）；是否允许为空/越界由实现校验
     * @param content 要持久化的内容；null 的处理由实现决定
     * @throws com.learn.mycc.core.exception.MyccException 写入失败（IO/实现异常）时抛出
     */
    void write(String key, String content);

    /**
     * 读取 key 对应的内容。
     *
     * @param key 存储键
     * @return 内容存在时返回对应值；不存在时返回 {@link Optional#empty()}
     * @throws com.learn.mycc.core.exception.MyccException 读取失败（IO/实现异常）时抛出
     */
    Optional<String> read(String key);

    /** 删除 key 对应的内容；不存在返回 false，成功删除返回 true。
     *
     * @param key 存储键
     * @return 原本存在且删除成功返回 true；原本不存在返回 false
     * @throws com.learn.mycc.core.exception.MyccException 删除失败（IO/实现异常）时抛出
     */
    boolean delete(String key);

    /**
     * 列出所有已存在的 key（相对路径、按序）。
     *
     * @return 全部 key 的不可变列表；无数据时返回空列表
     * @throws com.learn.mycc.core.exception.MyccException 遍历失败（IO/实现异常）时抛出
     */
    List<String> keys();

    /**
     * 返回 key 对应内容最后修改时间的 epoch 毫秒。
     *
     * <p>用途：供上层判断数据是否过期/需要刷新，比读取完整内容更轻量。</p>
     *
     * @param key 存储键
     * @return 最后修改时间（epoch 毫秒）；内容不存在返回 {@link Optional#empty()}
     * @throws com.learn.mycc.core.exception.MyccException 读取修改时间失败（IO/实现异常）时抛出
     */
    Optional<Long> lastModified(String key);
}
