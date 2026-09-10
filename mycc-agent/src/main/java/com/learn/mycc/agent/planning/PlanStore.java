package com.learn.mycc.agent.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.storage.spi.Storage;

import java.util.Optional;

/**
 * 计划落盘：把 {@link Plan} 按会话 id 序列化 JSON 存入 {@link Storage}，可按 id 原样恢复。
 * key 约定：{@code plan/<sessionId>.json}，与 {@code SessionStore} 的 {@code session/} 命名空间隔离。
 * 不维护"最近计划"指针——计划由 LLM 工具在当前会话内读写，生命周期随会话。
 */
@Component
public class PlanStore {

    private static final String PREFIX = "plan/";
    private static final String SUFFIX = ".json";

    private final Storage storage;
    /** JSON 序列化/反序列化器（不共享，实例内使用）。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /** @param storage 底层存储后端（文件/内存等），不可为 null。 */
    @Inject
    public PlanStore(Storage storage) {
        this.storage = storage;
    }

    /**
     * 按会话 id 加载计划。
     *
     * @param sessionId 会话 id，即存储 key 的一部分
     * @return 对应计划；不存在时返回空
     * @throws MyccException 反序列化失败时抛出
     */
    public Optional<Plan> load(String sessionId) {
        Optional<String> json = storage.read(key(sessionId));
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json.get(), Plan.class));
        } catch (JsonProcessingException e) {
            throw new MyccException("反序列化计划失败: " + sessionId, e);
        }
    }

    /**
     * 落盘计划：按会话 id 覆盖写。
     *
     * @param sessionId 会话 id
     * @param plan      待保存的计划
     * @throws MyccException 序列化失败时抛出
     */
    public void save(String sessionId, Plan plan) {
        try {
            storage.write(key(sessionId), mapper.writeValueAsString(plan));
        } catch (JsonProcessingException e) {
            throw new MyccException("序列化计划失败: " + sessionId, e);
        }
    }

    /**
     * 删除计划：任务全部完成即清理，避免残留文件与续聊读到旧计划。
     * 会话原本无计划时不抛异常（幂等）。
     *
     * @param sessionId 会话 id
     */
    public void delete(String sessionId) {
        storage.delete(key(sessionId));
    }

    /** 由会话 id 拼成存储 key：{@code plan/<id>.json}。 */
    private static String key(String sessionId) {
        return PREFIX + sessionId + SUFFIX;
    }
}