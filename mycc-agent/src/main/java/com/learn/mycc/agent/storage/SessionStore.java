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
 */
public final class SessionStore {

    private static final String PREFIX = "session/";
    private static final String SUFFIX = ".json";
    private static final String LATEST_KEY = PREFIX + "latest";
    private static final TypeReference<List<Message>> MESSAGE_LIST = new TypeReference<>() {};

    private final Storage storage;
    private final ObjectMapper mapper = new ObjectMapper();

    public SessionStore(Storage storage) {
        this.storage = storage;
    }

    public void save(Session session) {
        try {
            String json = mapper.writeValueAsString(session.conversation().messages());
            storage.write(key(session.id()), json);
            storage.write(LATEST_KEY, session.id());
        } catch (JsonProcessingException e) {
            throw new MyccException("序列化会话失败: " + session.id(), e);
        }
    }

    /** 返回最近一次保存的会话；从未保存过时返回空。 */
    public Optional<Session> latest() {
        Optional<String> id = storage.read(LATEST_KEY);
        return id.isEmpty() ? Optional.empty() : load(id.get());
    }


    public record SessionSummary(String id, String title) {}


    public List<SessionSummary> list() {
        return storage.keys().stream()
                .filter(k -> k.startsWith(PREFIX) && k.endsWith(SUFFIX))
                .sorted(Comparator.comparing((String k) -> storage.lastModified(k).orElse(0L)).reversed())
                .map(k -> new SessionSummary(idOf(k), lastUserMessageOf(sessionOf(idOf(k)))))
                .toList();
    }

    private static String idOf(String key) {
        return key.substring(PREFIX.length(), key.length() - SUFFIX.length());
    }

    private Session sessionOf(String id) {
        return load(id).orElseGet(Session::create);
    }

    private static String lastUserMessageOf(Session session) {
        return session.conversation().messages().stream()
                .filter(m -> m.role() == ChatMessage.Role.USER)
                .reduce((first, second) -> second)
                .map(Message::content)
                .orElse("（空对话）");
    }


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

    private static String key(String id) {
        return PREFIX + id + SUFFIX;
    }
}
