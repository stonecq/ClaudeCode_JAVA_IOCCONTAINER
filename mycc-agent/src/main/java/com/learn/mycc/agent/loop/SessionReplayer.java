package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;

/**
 * 会话历史回放：把已持久化的 {@link Session} 消息序列，转成与实时对话一致的事件流
 * （USER / TOOL_CALL / TOOL_RESULT / TOKEN / DONE）经 {@link InteractionPort} 下发。
 * <p>
 * 设计要点：历史回放与实时（{@link AgentLoop}）走同一事件协议、由同一 UI 渲染，
 * 保证「历史里看到的样子」与「当时实时看到的样子」一致；工具调用文本由共享的
 * {@link ToolCallFormatter} 格式化，避免重复实现。
 */
public final class SessionReplayer {

    private SessionReplayer() {
    }

    /**
     * 把会话历史逐条回放为输出事件，经 {@code port} 下发。
     * <p>
     * 映射规则（与实时一致）：USER → USER；助手带工具先 TOOL_CALL、正文非空再 TOKEN；
     * 工具结果 → TOOL_RESULT；最后以 DONE 收尾。空会话直接 DONE。
     *
     * @param session 要回放历史的会话，不可为 null
     * @param port    事件下发端口，不可为 null
     */
    public static void replay(Session session, InteractionPort port) {
        long seq = 0;
        String sessionId = session.id();
        for (Message m : session.conversation().messages()) {
            switch (m.role()) {
                case USER -> port.onEvent(new OutputEvent(OutputEventType.USER, m.content(), sessionId, seq++));
                case ASSISTANT -> {
                    if (m.hasToolCalls()) {
                        port.onEvent(new OutputEvent(OutputEventType.TOOL_CALL,
                                ToolCallFormatter.format(m.toolCalls()), sessionId, seq++));
                    }
                    if (!m.content().isBlank()) {
                        port.onEvent(new OutputEvent(OutputEventType.TOKEN, m.content(), sessionId, seq++));
                    }
                }
                case TOOL -> port.onEvent(new OutputEvent(OutputEventType.TOOL_RESULT, m.content(), sessionId, seq++));
                default -> { /* 未知角色忽略，向前兼容 */ }
            }
        }
        port.onEvent(new OutputEvent(OutputEventType.DONE, "", sessionId, seq));
    }
}
