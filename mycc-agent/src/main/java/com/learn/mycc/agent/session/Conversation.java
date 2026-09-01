package com.learn.mycc.agent.session;

import com.learn.mycc.ai.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/** 会话消息历史：追加消息，并按序转换为 LLM 请求用的 ChatMessage 列表。 */
public final class Conversation {

    private final List<Message> messages = new ArrayList<>();

    public void add(Message message) {
        messages.add(message);
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    public List<ChatMessage> toChatMessages() {
        return messages.stream().map(Message::toChatMessage).toList();
    }

    public boolean isEmpty(){
        return messages.isEmpty();
    }
}
