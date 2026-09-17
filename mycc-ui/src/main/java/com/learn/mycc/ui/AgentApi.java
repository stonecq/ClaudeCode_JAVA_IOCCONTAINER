package com.learn.mycc.ui;

import java.util.List;

/**
 * Agent 门面：<b>UI 与 agent 之间的唯一契约</b>。
 * <p>UI 只依赖本接口，不直接持有/获取 agent 的内部 bean（{@code SessionStore}/{@code AgentLoop}/
 * {@code ConfigService}/容器等）；返回值均为视图 record，不外泄内部类型。会话与对话无状态——
 * "当前会话"由 UI 侧维护。事件输出经 UI 提供的 {@link InteractionPort}（由 {@code UiAdapter} 提供）。</p>
 * <p>与 {@link InteractionPort} 同为 UI↔agent 的契约，故同处 ui 模块：一个管"agent → UI"输出、
 * 一个管"UI → agent"调用。</p>
 */
public interface AgentApi {

    /** 会话列表（标题/时间，最新在前）。 */
    List<SessionView> listSessions();

    /** 新建空会话，返回其 id。 */
    String createSession();

    /** 删除会话。 */
    void deleteSession(String id);

    /** 某会话的历史消息视图（不存在返回空列表）；渲染方式由各 UI 自定。 */
    List<MessageView> history(String id);

    /** 驱动一轮对话：消息交给 agent，输出经 UI 的输出端口下发。 */
    void chat(String sessionId, String userMessage);

    /** 当前注册的工具（名称 + 描述）。 */
    List<ToolView> listTools();
}