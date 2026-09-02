package com.learn.mycc.ui;

/**
 * 界面端口：agent 只通过它下发 {@link OutputEvent}，不直接碰终端/网络。
 *
 * <p>设计思路：这是 agent 侧与 UI 侧的解耦点（依赖倒置）。agent 面向本接口
 * 编程，只关心"事件被送出"，不关心 CLI 终端还是 SSE/Web 实现；具体渲染交给
 * 实现方。CLI 与 v2 Web 各提供一个实现即可复用同一套 agent 逻辑。</p>
 */
public interface InteractionPort {

    /**
     * 下发一个输出事件给 UI 层。
     *
     * <p>实现通常负责按事件类型渲染（如 TOKEN 追加到终端、DONE 结束一轮），
     * 调用方保证事件按序（seq 递增）到达。</p>
     *
     * @param event 要下发的输出事件，不能为 null
     */
    void onEvent(OutputEvent event);
}
