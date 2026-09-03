package com.learn.mycc.agent.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.storage.spi.Storage;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 会话落盘：把 Session 消息历史（含工具调用与结果）序列化为 JSON 存入 {@link Storage}，
 * 可按 id 原样恢复，并维护一个"最近会话"指针用于跨进程续聊。
 * key 约定：会话 {@code session/<id>.json}，最近指针 {@code session/latest}。
 *
 * <p>职责边界：只负责"序列化 + 按 key 存取"，不感知消息具体语义，
 * 换存储后端仅换 {@link Storage} 实现即可。
 */
public final class SessionStore {

    /** key 前缀，区分不同命名空间；配合 {@link #SUFFIX} 用于 {@link #list} 过滤会话文件。 */
    private static final String PREFIX = "session/";
    /** 会话文件后缀，用于区分会话 key 与 latest 指针 key。 */
    private static final String SUFFIX = ".json";
    /** "最近会话"指针 key：保存最近一次写入的会话 id，供跨进程恢复续聊。 */
    private static final String LATEST_KEY = PREFIX + "latest";
    /** 消息列表反序列化类型引用，供 Jackson 恢复 List&lt;Message&gt;。 */
    private static final TypeReference<List<Message>> MESSAGE_LIST = new TypeReference<>() {};

    private final Storage storage;
    /** JSON 序列化/反序列化器（不共享，实例内使用）。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /** @param storage 底层存储后端（文件/内存等），不可为 null。 */
    public SessionStore(Storage storage) {
        this.storage = storage;
    }

    /** 落盘会话：先把消息历史序列化为 JSON 写入会话 key，再更新 latest 指针。
     *  @param session 待持久化的会话；其 id 即存储 key
     *  @throws MyccException 当序列化失败时抛出 */
    public void save(Session session) {
        try {
            String json = mapper.writeValueAsString(session.conversation().messages());
            storage.write(key(session.id()), json);
            storage.write(LATEST_KEY, session.id());
        } catch (JsonProcessingException e) {
            throw new MyccException("序列化会话失败: " + session.id(), e);
        }
    }

    /** @return 最近一次保存的会话；从未保存过时返回空。 */
    public Optional<Session> latest() {
        Optional<String> id = storage.read(LATEST_KEY);
        return id.isEmpty() ? Optional.empty() : load(id.get());
    }


    /** 会话列表摘要：仅含展示用信息，避免加载整段历史；
     *  {@code title} 取最后一条用户消息，{@code lastModified} 为存储修改时间（epoch 毫秒）。 */
    public record SessionSummary(String id, String title, long lastModified) {}


    /** @return 所有已存会话的摘要列表，按最后修改时间倒序（最新在前）；
     *         latest 指针 key 本身被过滤。 */
    public List<SessionSummary> list() {
        // 只取会话 key（PREFIX+SUFFIX），自然排除 latest 指针；按修改时间倒序展示最近会话。
        return storage.keys().stream()
                .filter(k -> k.startsWith(PREFIX) && k.endsWith(SUFFIX))
                .sorted(Comparator.comparing((String k) -> storage.lastModified(k).orElse(0L)).reversed())
                .map(k -> new SessionSummary(idOf(k), lastUserMessageOf(sessionOf(idOf(k))), storage.lastModified(k).orElse(0L)))
                .toList();
    }

    /** 从存储 key 还原会话 id：剥掉 PREFIX 与 SUFFIX。 */
    private static String idOf(String key) {
        return key.substring(PREFIX.length(), key.length() - SUFFIX.length());
    }

    /** 按 id 加载；加载失败（数据损坏）时兜底返回新建空会话，避免 list 中途崩掉。 */
    private Session sessionOf(String id) {
        return load(id).orElseGet(Session::create);
    }

    /** 提取展示标题：取最后一条用户消息；空对话时返回占位文案 {@code （空对话）}。 */
    private static String lastUserMessageOf(Session session) {
        return session.conversation().messages().stream()
                .filter(m -> m.role() == ChatMessage.Role.USER)
                .reduce((first, second) -> second)
                .map(Message::content)
                .orElse("（空对话）");
    }


    /** @param id 会话 id
     *  @return 按 id 加载并重建会话（含完整消息历史）；不存在时返回空
     *  @throws MyccException 当 JSON 反序列化失败时抛出 */
    public Optional<Session> load(String id) {
        Optional<String> json = storage.read(key(id));
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<Message> messages = mapper.readValue(json.get(), MESSAGE_LIST);
            Session session = new Session(id);
            messages.forEach(session::addMessage);
            return Optional.of(session);
        } catch (JsonProcessingException e) {
            throw new MyccException("反序列化会话失败: " + id, e);
        }
    }

    /** 由会话 id 拼成存储 key：{@code session/<id>.json}。 */
    private static String key(String id) {
        return PREFIX + id + SUFFIX;
    }
}
