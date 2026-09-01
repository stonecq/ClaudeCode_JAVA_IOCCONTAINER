package com.learn.mycc.agent.session;

import java.util.UUID;

/** 一次会话：唯一 id + 消息历史。 */
public final class Session {

    private final String id;
    private final Conversation conversation = new Conversation();

    public Session(String id) {
        this.id = id;
    }

    public static Session create() {
        return new Session(UUID.randomUUID().toString());
    }

    public String id() {
        return id;
    }

    public Conversation conversation() {
        return conversation;
    }

    public void addMessage(Message message) {
        this.conversation.add(message);
    }

    public boolean isEmpty(){
        return this.conversation.isEmpty();
    }
}
