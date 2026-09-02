package com.learn.mycc.agent.loop;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 主循环：多轮调 LLM → 有工具调用则执行并回填消息历史 → 无工具调用则输出最终正文结束。
 * 只通过 {@link InteractionPort} 下发 {@link OutputEvent}；工具失败回填给 LLM，不崩会话。
 * 可选注入 {@link SessionStore}：注入后每轮 {@link #run} 结束落盘，会话由调用方显式绑定。
 */
public final class AgentLoop {

    private final InteractionPort port;
    private final LlmProvider provider;
    private final ToolCallExecutor executor;
    private final List<ToolSpec> tools;
    private final String model;
    private final int maxIterations;
    private final Session session;
    private final SessionStore storage;
    private long seq = 0;

    public AgentLoop(InteractionPort port, LlmProvider provider, ToolCallExecutor executor,
                     List<ToolSpec> tools, String model, int maxIterations) {
        this(port, provider, executor, tools, model, maxIterations, null, Session.create());
    }

    /** @param storage 会话存储；null 表示不持久化。@param session 已绑定会话（续聊/新对话由调用方选定）。 */
    public AgentLoop(InteractionPort port, LlmProvider provider, ToolCallExecutor executor,
                     List<ToolSpec> tools, String model, int maxIterations, SessionStore storage, Session session) {
        this.port = port;
        this.provider = provider;
        this.executor = executor;
        this.tools = List.copyOf(tools);
        this.model = model;
        this.maxIterations = maxIterations;
        this.storage = storage;
        this.session = session;
    }

    /** 从工具注册表装配：不持久化、新建会话。 */
    public static AgentLoop withToolRegistry(InteractionPort port, LlmProvider provider,
                                             ToolRegistry toolRegistry, String model, int maxIterations) {
        return withToolRegistry(port, provider, toolRegistry, model, maxIterations, null, Session.create());
    }

    /** 从工具注册表装配并绑定指定会话；storage 为 null 表示不持久化。 */
    public static AgentLoop withToolRegistry(InteractionPort port, LlmProvider provider,
                                             ToolRegistry toolRegistry, String model, int maxIterations,
                                             SessionStore storage, Session session) {
        ParameterSchemaGenerator schemaGenerator = new ParameterSchemaGenerator();
        List<ToolSpec> specs = toolRegistry.getAll().stream()
                .map(definition -> new ToolSpec(definition.getName(), definition.getDescription(),
                        schemaGenerator.generate(definition.getMethod())))
                .toList();
        return new AgentLoop(port, provider, new ToolCallExecutor(toolRegistry), specs, model, maxIterations, storage, session);
    }

    public Session session() {
        return session;
    }

    /** 以一条用户消息开始一轮对话，返回最终正文；Provider 错误经 ERROR 事件下发并作为返回值。每轮结束若有存储则落盘。 */
    public String run(String userMessage) {
        session.addMessage(Message.user(userMessage));
        try {
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                ChatResponse response = callProvider(buildRequest());
                if (response.hasToolCalls()) {
                    handleToolCalls(response);
                    continue;
                }
                String finalText = response.content();
                session.addMessage(Message.assistant(finalText, List.of()));
                emit(OutputEventType.DONE, finalText);
                return finalText;
            }
            String note = "已达到最大迭代次数（" + maxIterations + "），提前结束。";
            emit(OutputEventType.DONE, note);
            return note;
        } catch (MyccException e) {
            emit(OutputEventType.ERROR, e.getMessage());
            return e.getMessage();
        } finally {
            if (storage != null) {
                storage.save(session);
            }
        }
    }

    private ChatRequest buildRequest() {
        return ChatRequest.of(model, session.conversation().toChatMessages()).withTools(tools);
    }

    private ChatResponse callProvider(ChatRequest request) {
        AtomicReference<ChatResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        provider.chat(request, new StreamSink() {
            @Override
            public void onChunk(StreamChunk chunk) {
                if (chunk.reasoningContent() != null && !chunk.reasoningContent().isBlank()){
                    emit(OutputEventType.THINKING, chunk.reasoningContent());
                }
                if (chunk.content() != null && !chunk.content().isBlank()){
                    emit(OutputEventType.TOKEN, chunk.content());
                }
            }

            @Override
            public void onComplete(ChatResponse response) {
                responseRef.set(response);
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
            }
        });
        if (errorRef.get() != null) {
            throw new MyccException("LLM 调用失败: " + errorRef.get().getMessage(), errorRef.get());
        }
        return responseRef.get();
    }

    private void handleToolCalls(ChatResponse response) {
        String content = response.content();
        List<ToolCall> toolCalls = response.toolCalls();
        session.addMessage(Message.assistant(content, toolCalls));
        emit(OutputEventType.TOOL_CALL, formatToolCalls(toolCalls));
        for (ToolCall call : toolCalls) {
            ToolResult result = executor.execute(call);
            session.addMessage(Message.tool(call.id(), result.output()));
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
