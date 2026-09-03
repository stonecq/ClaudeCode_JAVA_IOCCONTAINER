package com.learn.mycc.cli;

import com.learn.mycc.cli.ReplLoop.LineInput;
import com.learn.mycc.cli.repl.CliPermissionPrompt;
import com.learn.mycc.core.permission.UserConfirmation.ConfirmChoice;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CliPermissionPromptTest {

    private final StringWriter buffer = new StringWriter();
    private final PrintWriter out = new PrintWriter(buffer);

    private static LineInput lines(String text) {
        return new java.io.BufferedReader(new StringReader(text))::readLine;
    }

    private CliPermissionPrompt prompt(LineInput input) {
        return new CliPermissionPrompt(input, out);
    }

    @Test
    void yAllowsOnce() {
        assertThat(prompt(lines("y\n")).prompt("bash", "执行 shell 命令", "{\"command\":\"echo hi\"}"))
                .isEqualTo(ConfirmChoice.ALLOW_ONCE);
        assertThat(buffer.toString()).contains("工具 bash 需审批").contains("参数: {\"command\":\"echo hi\"}");
    }

    @Test
    void nDenies() {
        assertThat(prompt(lines("N\n")).prompt("bash", "执行 shell 命令", "{}"))
                .isEqualTo(ConfirmChoice.DENY);
    }

    @Test
    void aAllowsAlways() {
        assertThat(prompt(lines("a\n")).prompt("bash", "执行 shell 命令", "{}"))
                .isEqualTo(ConfirmChoice.ALLOW_ALWAYS);
    }

    @Test
    void eofReturnsUnavailable() {
        assertThat(prompt(lines("")).prompt("bash", "执行 shell 命令", "{}"))
                .isEqualTo(ConfirmChoice.UNAVAILABLE);
    }

    @Test
    void blankLineReturnsUnavailable() {
        assertThat(prompt(lines("\n")).prompt("bash", "执行 shell 命令", "{}"))
                .isEqualTo(ConfirmChoice.UNAVAILABLE);
    }

    @Test
    void ioErrorReturnsUnavailable() {
        LineInput broken = () -> {
            throw new IOException("no tty");
        };
        assertThat(prompt(broken).prompt("bash", "执行 shell 命令", "{}"))
                .isEqualTo(ConfirmChoice.UNAVAILABLE);
    }

    @Test
    void invalidChoiceRepromptsUntilValid() {
        assertThat(prompt(lines("x\ny\n")).prompt("bash", "执行 shell 命令", "{}"))
                .isEqualTo(ConfirmChoice.ALLOW_ONCE);
        assertThat(buffer.toString()).contains("无效输入");
    }
}