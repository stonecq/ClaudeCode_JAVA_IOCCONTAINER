package com.learn.mycc.cli.repl;

import com.learn.mycc.cli.ReplLoop.LineInput;
import com.learn.mycc.core.permission.UserConfirmation;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * 终端人工审批实现（M8 权限管理）：以 {@code [y/N/a]} 行交互征求用户选择。
 * <p>仅当 {@link com.learn.mycc.core.permission.PermissionPolicy} 对某次调用产出 ASK
 * 时被触发，输出工具名、参数与三种选择，循环读入直至拿到合法输入。
 * 无输入（EOF）、空行或 IO 异常一律返回 {@link ConfirmChoice#UNAVAILABLE}，
 * 交由上层按 fail-closed（拒绝）降级，保证 headless 环境不会误放行。</p>
 */
public final class CliPermissionPrompt implements UserConfirmation {

    private final LineInput input;
    private final PrintWriter out;

    public CliPermissionPrompt(LineInput input, PrintWriter out) {
        this.input = input;
        this.out = out;
    }

    /**
     * 输出审批提示并循环读入用户选择。
     *
     * @return 用户选择；EOF / 空行 / IO 异常返回 {@link ConfirmChoice#UNAVAILABLE}
     */
    @Override
    public ConfirmChoice prompt(String toolName, String description, String args) {
        out.println("工具 " + toolName + " 需审批（高风险）");
        out.println("  描述: " + description);
        out.println("  参数: " + args);
        out.println("  [y]允许本次 [N]拒绝 [a]始终允许");
        out.flush();
        while (true) {
            String line;
            try {
                line = input.readLine();
            } catch (IOException e) {
                // 终端读取失败按不可用降级，fail-closed 拒绝本次调用
                return ConfirmChoice.UNAVAILABLE;
            }
            if (line == null) {
                return ConfirmChoice.UNAVAILABLE;
            }
            String choice = line.trim();
            if (choice.isEmpty()) {
                return ConfirmChoice.UNAVAILABLE;
            }
            ConfirmChoice result = switch (choice.toLowerCase(Locale.ROOT)) {
                case "y" -> ConfirmChoice.ALLOW_ONCE;
                case "n" -> ConfirmChoice.DENY;
                case "a" -> ConfirmChoice.ALLOW_ALWAYS;
                default -> null;
            };
            if (result != null) {
                return result;
            }
            out.println("无效输入，请选择 [y/N/a]");
            out.flush();
        }
    }
}