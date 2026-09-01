package com.learn.mycc.ui;

/** 输出事件类型：agent 到 UI 的公共协议。 */
public enum OutputEventType {
    THINKING,
    /** 流式正文增量 */
    TOKEN,
    /** agent 发起工具调用 */
    TOOL_CALL,
    /** 工具执行结果 */
    TOOL_RESULT,
    /** 错误 */
    ERROR,
    /** 本轮对话结束 */
    DONE
}
