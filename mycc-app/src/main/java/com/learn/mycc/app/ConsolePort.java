package com.learn.mycc.app;

import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.OutputEvent;
import com.learn.mycc.ui.OutputEventType;

import java.io.PrintStream;

/**
 * 控制台渲染实现：把 {@link OutputEvent} 逐个打印到流（M6 用 JLine/ANSI 的 CliPort 替代）。
 * <p>
 * 职责边界：纯「输出渲染」层，只负责把事件转成终端文本，不关心对话流程。
 * 实现的是 {@link InteractionPort} 端口，因此 agent 循环只依赖接口，可随时切换其他 UI 实现。
 */
public final class ConsolePort implements InteractionPort {

    /** 输出目标流，通常为 System.out；决定渲染结果去哪，不可为 null。 */
    private final PrintStream out;
    /** 是否显示思考内容；默认隐藏，仅影响 THINKING 事件的渲染。 */
    private final boolean showReasoning;
    /**
     * 是否正处在思考块中：首个 THINKING 前打 [思考] 前缀，下一个非思考事件换行收块。
     * 生命周期：首个 THINKING 置 true，随后非思考事件由 closeThinking() 复位为 false。
     */
    private boolean inThinking;

    /** 以不显示思考内容的方式构造控制台端口（等价 showReasoning=false）。 */
    public ConsolePort(PrintStream out) {
        this(out, false);
    }

    /**
     * 构造控制台端口。
     *
     * @param out            渲染输出流（如 {@code System.out}），不可为 null
     * @param showReasoning  是否显示 LLM 思考过程；true 显示，false 则跳过 THINKING 事件
     */
    public ConsolePort(PrintStream out, boolean showReasoning) {
        this.out = out;
        this.showReasoning = showReasoning;
    }

    /**
     * 处理单个输出事件，按 {@link OutputEventType} 分发到对应渲染分支。
     * <p>
     * 设计要点：THINKING 事件为「流式追加」，可能连续多次到达，因此维护 inThinking 状态
     * 只在块首打一次前缀；其余事件先 closeThinking() 收块，避免多行内容粘连。
     *
     * @param event 待渲染的输出事件，不可为 null
     */
    @Override
    public void onEvent(OutputEvent event) {
        switch (event.type()) {
            case THINKING -> {
                // 未开启推理展示时，思考内容直接丢弃，不产生任何输出
                if (!showReasoning) {
                    return;
                }
                // 仅块首打印 [思考] 前缀，后续分片直接拼接到同一行
                if (!inThinking) {
                    out.print("[思考] ");
                    inThinking = true;
                }
                out.print(event.payload());
            }
            case TOKEN -> {
                // 正常的 token 流：先收掉思考块，再继续打印正文
                closeThinking();
                out.print(event.payload());
            }
            case TOOL_CALL -> {
                closeThinking();
                out.println("[工具] " + event.payload());
            }
            case TOOL_RESULT -> {
                closeThinking();
                out.println("[结果] " + event.payload());
            }
            case ERROR -> {
                closeThinking();
                out.println("[错误] " + event.payload());
            }
            case DONE -> {
                // 对话结束：收掉思考块并输出一个空行，让下一轮 "你 > " 提示符另起一行
                closeThinking();
                out.println();
            }
        }
    }

    /**
     * 若正处于思考块中，则换行收块并复位 inThinking。
     * 保证思考内容后视觉上换行，不会与后续正文/前缀粘连。
     */
    private void closeThinking() {
        if (inThinking) {
            out.println();
            inThinking = false;
        }
    }
}