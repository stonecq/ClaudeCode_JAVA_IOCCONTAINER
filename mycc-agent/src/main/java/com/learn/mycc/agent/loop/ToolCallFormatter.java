package com.learn.mycc.agent.loop;

import com.learn.mycc.ai.model.ToolCall;

import java.util.List;

/**
 * 工具调用列表的共享格式化：实时（{@link AgentLoop} 下发 TOOL_CALL）与历史回放
 * （{@link SessionReplayer}）统一用同一实现，保证「工具调用」在实时与历史里显示一致。
 */
final class ToolCallFormatter {

    private ToolCallFormatter() {
    }

    /** 把多个工具调用格式化为可读文本（如 fn1(args); fn2(args)）；无调用时返回空串。 */
    static String format(List<ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(call -> call.name() + "(" + call.arguments() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}
