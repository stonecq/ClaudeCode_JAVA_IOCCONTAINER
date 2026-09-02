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
 */
public final class SessionMenu {

    private static final int TITLE_LIMIT = 20;

    private final SessionStore store;
    private final PrintStream out;
    private final BufferedReader in;

    public SessionMenu(SessionStore store, PrintStream out, BufferedReader in) {
        this.store = store;
        this.out = out;
        this.in = in;
    }

    /** 让用户从历史会话中选择；返回已加载会话或新建会话。 */
    public Session select() throws IOException {
        List<SessionStore.SessionSummary> summaries = store.list();
        out.println("历史会话：");
        for (int i = 0; i < summaries.size(); i++) {
            out.println((i + 1) + ". " + truncate(summaries.get(i).title()));
        }
        out.println((summaries.size() + 1) + ". 新的对话");
        while (true) {
            out.print("请选择 > ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
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
                return store.load(summaries.get(choice - 1).id()).orElseGet(Session::create);
            }
            if (choice == summaries.size() + 1) {
                return Session.create();
            }
            out.println("无效选择，请重新输入。");
        }
    }

    /** 按角色标记打印会话历史；空会话打印占位符。 */
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
                    if (m.hasToolCalls()) {
                        out.println("[工具] " + formatToolCalls(m.toolCalls()));
                    }
                    if (!m.content().isBlank()) {
                        out.println("助手 > " + m.content());
                    }
                }
                case TOOL -> out.println("[结果] " + m.content());
                default -> { }
            }
        }
    }

    private static String truncate(String title) {
        if (title.codePointCount(0, title.length()) <= TITLE_LIMIT) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, TITLE_LIMIT)) + "…";
    }

    private static String formatToolCalls(List<ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(call -> call.name() + "(" + call.arguments() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}