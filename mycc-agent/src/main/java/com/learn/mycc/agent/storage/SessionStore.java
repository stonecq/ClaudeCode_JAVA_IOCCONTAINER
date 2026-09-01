package com.learn.mycc.agent.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.storage.spi.Storage;

import java.util.List;
import java.util.Optional;

/**
 * 会话落盘：把 Session 消息历史（含工具调用与结果）序列化为 JSON 存入 {@link Storage}，
 * 可按 id 原样恢复。key 约定为 {@code session/<id>.json}。
 */
public final class SessionStore {

    private static final String PREFIX = "session/";
    private static final String SUFFIX = ".json";
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
        } catch (JsonProcessingException e) {
            throw new MyccException("序列化会话失败: " + session.id(), e);
        }
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
