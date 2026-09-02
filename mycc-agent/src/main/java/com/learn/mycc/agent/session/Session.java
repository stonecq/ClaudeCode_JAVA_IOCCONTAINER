package com.learn.mycc.agent.session;

import java.util.UUID;

/**
 * 一次会话：唯一 id + 消息历史。作为 agent 循环与持久层之间传递的上下文载体，
 * 由调用方显式创建（新对话）或从存储恢复（续聊）后绑定到 {@code AgentLoop}。
 */
public final class Session {

    /** 会话唯一标识；新会话由 {@link #create} 生成 UUID，续聊会话为存储中的既有 id；
     *  一经创建不再变化。 */
    private final String id;
    /** 本会话的消息历史，随每轮 run 不断追加。 */
    private final Conversation conversation = new Conversation();

    /** @param id 会话 id（应为非 null 唯一值；恢复时会沿用持久化 id 以命中存储 key） */
    public Session(String id) {
        this.id = id;
    }

    /** @return 以随机 UUID 为 id 的新建空会话。 */
    public static Session create() {
        return new Session(UUID.randomUUID().toString());
    }

    /** @return 会话 id。 */
    public String id() {
        return id;
    }

    /** @return 本会话的消息历史对象（可变，结构共享）。 */
    public Conversation conversation() {
        return conversation;
    }

    /** 向会话历史追加一条消息。 */
    public void addMessage(Message message) {
        this.conversation.add(message);
    }

    /** @return 会话历史是否为空（尚无任何消息）。 */
    public boolean isEmpty(){
        return this.conversation.isEmpty();
    }
}
