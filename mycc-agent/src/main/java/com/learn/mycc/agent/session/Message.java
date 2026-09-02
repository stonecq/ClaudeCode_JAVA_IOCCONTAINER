package com.learn.mycc.agent.session;

import com.learn.mycc.ai.model.ChatMessage;
import com.learn.mycc.ai.model.ToolCall;

import java.util.List;

/**
 * 会话内消息（含工具调用/结果），与发给 LLM 的 {@link ChatMessage} 相互转换。
 * 会话状态与 LLM API 契约分开建模：换 Provider 时只改转换层，不改会话记录。
 * 以 record 实现不可变，天然线程安全、免手写 equals/hashCode。
 *
 * @param role      消息角色（USER/ASSISTANT/TOOL）；TOOL 消息有 toolCallId，其余为 null
 * @param content   文本正文；assistant 仅携带 toolCalls 时可为空，tool 消息为工具输出
 * @param toolCallId 工具调用关联 id；仅 TOOL 角色非 null，用于回填到对应调用
 * @param toolCalls 本消息携带的工具调用列表（仅 assistant 消息存在；构造时去空并冻结）
 */
public record Message(ChatMessage.Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    // 精简构造：工具列表空值统一归置为空不可变列表，避免下游判空与外部篡改。
    public Message {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** @return 用户输入消息：无工具调用、无关联 id。 */
    public static Message user(String content) {
        return new Message(ChatMessage.Role.USER, content, null, List.of());
    }

    /** @return 助手消息：可携带 0..n 个工具调用（调用方保证调用列表即本消息产物）。 */
    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(ChatMessage.Role.ASSISTANT, content, null, toolCalls);
    }

    /** @param callId 被回填的工具调用 id；@param output 工具执行输出文本 */
    public static Message tool(String callId, String output) {
        return new Message(ChatMessage.Role.TOOL, output, callId, List.of());
    }

    /** @return 本消息是否携带了需要执行的工具调用。 */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /** @return 转换为 LLM 请求用的 {@link ChatMessage}（字段一一映射）。 */
    public ChatMessage toChatMessage() {
        return new ChatMessage(role, content, toolCallId, toolCalls);
    }
}
