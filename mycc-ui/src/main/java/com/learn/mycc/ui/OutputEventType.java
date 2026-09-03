package com.learn.mycc.ui;

/**
 * 输出事件类型：agent 到 UI 的公共协议。
 *
 * <p>每种类型对应 agent 生命周期中的一类输出，UI 据此决定渲染方式（追加/整段
 * 覆盖/结束等）。</p>
 */
public enum OutputEventType {
    /** 用户输入回合：实时对话的用户提问（提示符取代）或历史回放的用户消息，payload 为用户输入文本 */
    USER,
    /** 思考过程：agent 的推理内容，通常可折叠或与正式回复分开展示 */
    THINKING,
    /** 流式正文增量：回复正文的一段，UI 通常持续追加拼接 */
    TOKEN,
    /** agent 发起工具调用：payload 为工具名与参数说明 */
    TOOL_CALL,
    /** 工具执行结果：payload 为工具返回的输出或摘要 */
    TOOL_RESULT,
    /** 错误：本轮出现异常，payload 为错误信息 */
    ERROR,
    /** 本轮对话结束：所有输出已下发完，UI 应收尾并可能进入新一轮输入 */
    DONE
}
