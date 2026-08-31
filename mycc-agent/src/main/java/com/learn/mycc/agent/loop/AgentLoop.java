package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.tool.ToolCallExecutor;
import com.learn.mycc.agent.tool.ToolResult;
import com.learn.mycc.ai.model.ChatRequest;
import com.learn.mycc.ai.model.ChatResponse;
import com.learn.mycc.ai.model.StreamChunk;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.ai.model.ToolSpec;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.ai.spi.StreamSink;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.tool.ParameterSchemaGenerator;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;

import java.util.List;

/**
 * Agent 主循环：多轮调 LLM → 有工具调用则执行并回填消息历史 → 无工具调用则输出最终正文结束。
 * 只通过 {@link InteractionPort} 下发 {@link OutputEvent}；工具失败回填给 LLM，不崩会话。
 */
public final class AgentLoop {

    private final InteractionPort port;
    private final LlmProvider provider;
    private final ToolCallExecutor executor;
    private final List<ToolSpec> tools;
    private final String model;
    private final int maxIterations;
    private final Session session = Session.create();
    private long seq = 0;

    public AgentLoop(InteractionPort port, LlmProvider provider, ToolCallExecutor executor,
                     List<ToolSpec> tools, String model, int maxIterations) {
        this.port = port;
        this.provider = provider;
        this.executor = executor;
        this.tools = List.copyOf(tools);
        this.model = model;
        this.maxIterations = maxIterations;
    }

    /** 从工具注册表装配：生成 ToolSpec（发给 LLM）并构造执行器。 */
    public static AgentLoop withToolRegistry(InteractionPort port, LlmProvider provider,
                                             ToolRegistry toolRegistry, String model, int maxIterations) {
        ParameterSchemaGenerator schemaGenerator = new ParameterSchemaGenerator();
        List<ToolSpec> specs = toolRegistry.getAll().stream()
                .map(definition -> new ToolSpec(definition.getName(), definition.getDescription(),
                        schemaGenerator.generate(definition.getMethod())))
                .toList();
        return new AgentLoop(port, provider, new ToolCallExecutor(toolRegistry), specs, model, maxIterations);
    }

    public Session session() {
        return session;
    }

    /** 以一条用户消息开始一轮对话，返回最终正文；Provider 错误经 ERROR 事件下发并作为返回值。 */
    public String run(String userMessage) {
        session.conversation().add(Message.user(userMessage));
        try {
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                ChatResponse response = callProvider(buildRequest());
                if (response.hasToolCalls()) {
                    handleToolCalls(response.toolCalls());
                    continue;
                }
                String finalText = response.content();
                session.conversation().add(Message.assistant(finalText, List.of()));
                emit(OutputEventType.DONE, finalText);
                return finalText;
            }
            String note = "已达到最大迭代次数（" + maxIterations + "），提前结束。";
            emit(OutputEventType.DONE, note);
            return note;
        } catch (MyccException e) {
            emit(OutputEventType.ERROR, e.getMessage());
            return e.getMessage();
        }
    }

    private ChatRequest buildRequest() {
        return ChatRequest.of(model, session.conversation().toChatMessages()).withTools(tools);
    }

    private ChatResponse callProvider(ChatRequest request) {
        ChatResponse[] responseRef = new ChatResponse[1];
        Throwable[] errorRef = new Throwable[1];
        provider.chat(request, new StreamSink() {
            @Override
            public void onChunk(StreamChunk chunk) {
                emit(OutputEventType.TOKEN, chunk.content());
            }

            @Override
            public void onComplete(ChatResponse response) {
                responseRef[0] = response;
            }

            @Override
            public void onError(Throwable error) {
                errorRef[0] = error;
            }
        });
        if (errorRef[0] != null) {
            throw new MyccException("LLM 调用失败: " + errorRef[0].getMessage(), errorRef[0]);
        }
        return responseRef[0];
    }

    private void handleToolCalls(List<ToolCall> toolCalls) {
        session.conversation().add(Message.assistant("", toolCalls));
        emit(OutputEventType.TOOL_CALL, formatToolCalls(toolCalls));
        for (ToolCall call : toolCalls) {
            ToolResult result = executor.execute(call);
            session.conversation().add(Message.tool(call.id(), result.output()));
            emit(OutputEventType.TOOL_RESULT, result.callId() + " => " + result.output());
        }
    }

    private static String formatToolCalls(List<ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(call -> call.name() + "(" + call.arguments() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private void emit(OutputEventType type, String payload) {
        port.onEvent(new OutputEvent(type, payload, session.id(), seq++));
    }
}
