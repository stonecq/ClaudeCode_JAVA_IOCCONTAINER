package com.learn.mycc.agent.session;

import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void convertsToChatMessagesPreservingOrderAndToolMetadata() {
        Conversation conversation = new Conversation();
        conversation.add(Message.user("hi"));
        conversation.add(Message.assistant("", List.of(new ToolCall("c1", "read_file", "{}"))));
        conversation.add(Message.tool("c1", "ok"));

        List<ChatMessage> chat = conversation.toChatMessages();

        assertThat(chat).extracting(ChatMessage::role)
                .containsExactly(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT, ChatMessage.Role.TOOL);
        assertThat(chat.get(1).hasToolCalls()).isTrue();
        assertThat(chat.get(1).toolCalls().get(0).id()).isEqualTo("c1");
        assertThat(chat.get(2).toolCallId()).isEqualTo("c1");
        assertThat(chat.get(2).content()).isEqualTo("ok");
    }

    @Test
    void exposesMessageSnapshotInOrder() {
        Conversation conversation = new Conversation();
        conversation.add(Message.user("hi"));
        conversation.add(Message.user("again"));

        assertThat(conversation.messages()).hasSize(2);
        assertThat(conversation.messages()).extracting(Message::content).containsExactly("hi", "again");
    }
}
