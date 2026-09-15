package com.learn.mycc.agent.session;

import com.learn.mycc.ai.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话消息历史：追加消息，并按序转换为发送给 LLM 的 {@link ChatMessage} 列表。
 * 与 {@link Session} 拆分：Session 负责会话元信息（id、归属），Conversation 专职消息序列的
 * 存储与 LLM 契约转换，各司其职、便于单独测试与复用。
 */
public final class Conversation {

    /** 消息按追加顺序保存；仅经 {@link #add} 修改，
     *  外部通过 {@link #messages()} 拿不可变快照。 */
    private final List<Message> messages = new ArrayList<>();

    /** 追加一条消息到历史末尾（保持入队顺序）。 */
    public void add(Message message) {
        messages.add(message);
    }

    /** 以给定列表整体替换消息历史（上下文压缩回写用）；传入 null 视为清空。 */
    public void replaceAll(List<Message> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
    }

    /** @return 当前全部消息的不可变快照，外部修改不会影响内部状态。 */
    public List<Message> messages() {
        return List.copyOf(messages);
    }

    /** @return 按序映射为 LLM 请求消息列表 {@code toChatMessage}（序即上下文序）。 */
    public List<ChatMessage> toChatMessages() {
        return messages.stream().map(Message::toChatMessage).toList();
    }

    /** @return 历史是否为空（尚未有任何消息）。 */
    public boolean isEmpty(){
        return messages.isEmpty();
    }
}
