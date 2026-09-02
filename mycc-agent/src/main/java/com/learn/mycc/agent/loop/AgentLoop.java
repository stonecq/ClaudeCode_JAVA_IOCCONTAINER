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

    /** UI 输出端口：所有 {@link OutputEvent}
     *  （THINKING/TOKEN/TOOL_CALL/TOOL_RESULT/DONE/ERROR）由此下发。 */
    private final InteractionPort port;
    /** LLM 提供方：负责真正的大模型调用（含流式回调）。 */
    private final LlmProvider provider;
    /** 工具执行器：解析工具调用并反射调用 @Tool 方法。 */
    private final ToolCallExecutor executor;
    /** 随每次请求发给 LLM 的工具声明列表；不可变拷贝，避免外部篡改。 */
    private final List<ToolSpec> tools;
    /** LLM 模型名，透传给 {@link ChatRequest}。 */
    private final String model;
    /** 单轮 run 的最大迭代次数上限，防止工具反复调用导致死循环；值域 ≥1。 */
    private final int maxIterations;
    /** 本次绑定的会话，累加本轮全部消息；由调用方选定（续聊/新对话）。 */
    private final Session session;
    /** 会话存储；null 表示不持久化（不落盘）。 */
    private final SessionStore storage;
    /** 事件序号计数器，从 0 递增，用于标识事件顺序；仅单线程 run 内安全递增。 */
    private long seq = 0;

    public AgentLoop(InteractionPort port, LlmProvider provider, ToolCallExecutor executor,
                     List<ToolSpec> tools, String model, int maxIterations) {
        this(port, provider, executor, tools, model, maxIterations, null, Session.create());
    }

    /**
     * 全量构造：指定会话与是否持久化。
     * @param port      UI 输出端口
     * @param provider  LLM 提供方
     * @param executor  工具执行器
     * @param tools     工具声明列表（内部不可变拷贝）
     * @param model     模型名
     * @param maxIterations 单轮最大迭代次数，值域 ≥1
     * @param storage   会话存储；null 表示不持久化
     * @param session   已绑定会话（续聊/新对话由调用方选定，不可为 null）
     */
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

    /**
     * 以一条用户消息开始一轮对话，返回最终正文。
     * 多轮迭代：每轮先调 LLM；若返回工具调用则执行并把结果回填给 LLM 继续下一轮，
     * 否则输出最终正文结束。
     * 每轮结束（含异常）在 finally 中若有存储则落盘，保证会话不因崩溃丢失。
     *
     * @param userMessage 用户输入（新增为 USER 消息；空字符串也会入历史，
     *                    调用方应自行校验）
     * @return 最终正文；达到 {@code maxIterations} 上限时返回提示文本，
     *         Provider 出错时返回错误信息（同时以 ERROR 事件下发）
     */
    public String run(String userMessage) {
        session.addMessage(Message.user(userMessage));
        try {
            // 有工具调用则继续下一轮，否则视为最终回答，输出正文并结束。
            // 用迭代上限而非 while(true) 兜底，防止工具反复调用导致死循环。
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                ChatResponse response = callProvider(buildRequest());
                if (response.hasToolCalls()) {
                    handleToolCalls(response);
                    continue;
                }
                // 无工具调用：视为 LLM 已给出最终回答，落回会话并结束本轮。
                String finalText = response.content();
                session.addMessage(Message.assistant(finalText, List.of()));
                emit(OutputEventType.DONE, finalText);
                return finalText;
            }
            String note = "已达到最大迭代次数（" + maxIterations + "），提前结束。";
            emit(OutputEventType.DONE, note);
            return note;
        } catch (MyccException e) {
            // Provider 层错误：ERROR 事件告知调用方并作为返回值，不抛出以免调用栈复杂化。
            emit(OutputEventType.ERROR, e.getMessage());
            return e.getMessage();
        } finally {
            // 无论如何（含异常与提前结束）均尝试落盘，避免上下文丢失。
            if (storage != null) {
                storage.save(session);
            }
        }
    }

    /** 组装本次请求：以当前会话完整历史 + 工具声明构造 ChatRequest。 */
    private ChatRequest buildRequest() {
        return ChatRequest.of(model, session.conversation().toChatMessages()).withTools(tools);
    }

    /** 发起 LLM 调用并收集流式结果。
     *  provider 以流式 {@link StreamSink} 回调各 chunk：思考内容→THINKING、正文→TOKEN。
     *  用 {@link AtomicReference} 在回调（可能任意线程）中暂存最终响应或错误。
     *  @throws MyccException 当 provider 回调 onError 时抛出，由 {@link #run} 统一兜底 */
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

    /** 执行一次响应中的全部工具调用：LLM 本轮正文与其工具调用先入会话，
     *  再逐个执行并把 tool 结果回填进消息历史。
     *  工具失败由 {@link ToolCallExecutor} 封装为 failure 而非抛异常，
     *  回填给 LLM 由其决定是否纠正。 */
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

    /** 把多个工具调用格式化为可读文本（如 fn1(args); fn2(args)）；无调用时返回空串。 */
    private static String formatToolCalls(List<ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(call -> call.name() + "(" + call.arguments() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    /** 统一事件出口：包成 {@link OutputEvent} 下发，并附带自增序号 seq 标识顺序。 */
    private void emit(OutputEventType type, String payload) {
        port.onEvent(new OutputEvent(type, payload, session.id(), seq++));
    }
}
