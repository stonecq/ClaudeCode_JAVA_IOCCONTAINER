package com.learn.mycc.app;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ToolCall;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * 启动时历史会话选择菜单 + 历史打印。编号由 1 开始，「新的对话」恒为最后一号。
 * 仅在存在历史会话时由应用调用 {@link #select()}；非法输入重读，Ctrl+D 视为新会话。
 * <p>
 * 职责边界：只负责「选会话」和「打印历史」两个纯交互动作，不参与后续对话逻辑，
 * 会话的加载/新建委托给 {@link SessionStore}，本类只做终端交互与格式排版。
 */
public final class SessionMenu {

    /** 菜单里展示的标题最大码点数，超长则截断加省略号。 */
    private static final int TITLE_LIMIT = 20;

    /** 会话持久化存储，负责 list 历史概览与按 id 加载/新建会话，不可为 null。 */
    private final SessionStore store;
    /** 菜单/历史输出流，通常为 System.out，不可为 null。 */
    private final PrintStream out;
    /** 用户输入读取器，读取每行选择；读到时 EOF（Ctrl+D）时 readLine 返回 null。 */
    private final BufferedReader in;

    /**
     * 构造会话选择菜单。
     *
     * @param store 会话存储，用于列举与加载历史，不可为 null
     * @param out   输出流（如 System.out），不可为 null
     * @param in    输入读取器，不可为 null
     */
    public SessionMenu(SessionStore store, PrintStream out, BufferedReader in) {
        this.store = store;
        this.out = out;
        this.in = in;
    }

    /**
     * 让用户选择历史会话：先列历史（含「新的对话」末位项），再循环读取合法编号。
     * <p>
     * 边界处理：Ctrl+D（readLine 返回 null）或选择「新的对话」均返回新会话；
     * 选择历史编号时按 id 加载，加载失败（记录可能已丢失）则回退为新建会话；
     * 非数字或超出范围的输入提示后重新读取，直到得到合法选择。
     *
     * @return 用户选中的历史会话（已从存储加载）或新建会话，非 null
     * @throws IOException 读取输入流发生 I/O 错误时抛出
     */
    public Session select() throws IOException {
        List<SessionStore.SessionSummary> summaries = store.list();
        out.println("历史会话：");
        // 历史会话从 1 开始编号，「新的对话」固定为最后一个编号
        for (int i = 0; i < summaries.size(); i++) {
            out.println((i + 1) + ". " + truncate(summaries.get(i).title()));
        }
        out.println((summaries.size() + 1) + ". 新的对话");
        while (true) {
            out.print("请选择 > ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
                // 用户 Ctrl+D：视为放弃选择，直接开启新会话
                return Session.create();
            }
            int choice;
            try {
                choice = Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                out.println("无效选择，请重新输入。");
                continue;
            }
            if (choice >= 1 && choice <= summaries.size()) {
                // 命中某条历史：按其 id 加载，加载失败（记录丢失）时回退为新会话
                return store.load(summaries.get(choice - 1).id()).orElseGet(Session::create);
            }
            if (choice == summaries.size() + 1) {
                return Session.create();
            }
            out.println("无效选择，请重新输入。");
        }
    }

    /**
     * 按角色标记打印会话历史，便于用户预览续聊前的内容。
     * <p>
     * 角色与展示对应：用户消息 → "我 > "；助手若带工具调用先写 "[工具] "，
     * 再在正文非空时写 "助手 > "；工具消息 → "[结果] "。空会话打印占位符提示。
     *
     * @param session 要打印历史的会话，可为空会话（打印 "（新会话）"），不可为 null
     */
    public void printHistory(Session session) {
        List<Message> messages = session.conversation().messages();
        if (messages.isEmpty()) {
            out.println("（新会话）");
            return;
        }
        for (Message m : messages) {
            switch (m.role()) {
                case USER -> out.println("我 > " + m.content());
                case ASSISTANT -> {
                    // 助手消息可能同时含 "调用工具" 与 "纯文本" 两部分，分别打印
                    if (m.hasToolCalls()) {
                        out.println("[工具] " + formatToolCalls(m.toolCalls()));
                    }
                    if (!m.content().isBlank()) {
                        out.println("助手 > " + m.content());
                    }
                }
                case TOOL -> out.println("[结果] " + m.content());
                default -> { /* 未知角色忽略，保证向前兼容 */ }
            }
        }
    }

    /**
     * 将标题截断到 {@link #TITLE_LIMIT} 个 Unicode 码点以内，超长则以省略号结尾。
     * <p>
     * 用码点而非 char 计数，是为了正确处理 emoji 等代理对字符，避免把
     * UTF-16 高/低代理项截成半个字符导致乱码。
     *
     * @param title 要截断的标题，可为空串
     * @return 未超长时的原标题，或截断加 "…" 后的新字符串
     */
    private static String truncate(String title) {
        if (title.codePointCount(0, title.length()) <= TITLE_LIMIT) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, TITLE_LIMIT)) + "…";
    }

    /**
     * 将一次助手回复中的多个工具调用格式化为可读字符串。
     *
     * @param toolCalls 工具调用列表，可为空
     * @return 形如 "name(args); name(args)" 的拼接结果；列表为空时返回空串
     */
    private static String formatToolCalls(List<ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(call -> call.name() + "(" + call.arguments() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}