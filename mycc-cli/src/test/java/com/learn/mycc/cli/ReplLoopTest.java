package com.learn.mycc.cli;

import com.learn.mycc.cli.ReplLoop.AgentRunner;
import com.learn.mycc.cli.ReplLoop.LineInput;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReplLoopTest {

    private final StringWriter buffer = new StringWriter();
    private final PrintWriter out = new PrintWriter(buffer);
    private final String nl = System.lineSeparator();

    private CliPort port(boolean ansi, boolean showReasoning) {
        return new CliPort(out, ansi, showReasoning);
    }

    private static LineInput input(String text) {
        BufferedReader reader = new BufferedReader(new StringReader(text));
        return () -> reader.readLine();
    }

    @Test
    void exitsOnSlashExitCommand() {
        List<String> ran = new ArrayList<>();
        new ReplLoop(port(false, true), ran::add, input("/exit\n"), "s1").run();

        assertThat(ran).isEmpty();
    }

    @Test
    void sendsUserEventBeforeRunningAgent() {
        List<String> ran = new ArrayList<>();
        new ReplLoop(port(false, true), ran::add, input("第一句\n/exit\n"), "s1").run();

        assertThat(ran).containsExactly("第一句");
        assertThat(buffer.toString()).isEqualTo("我 > 第一句" + nl);
    }

    @Test
    void toleratesRoundExceptionAndContinues() {
        AtomicInteger calls = new AtomicInteger();
        AgentRunner runner = msg -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
        };
        new ReplLoop(port(false, true), runner, input("坏句\n好句\n/exit\n"), "s1").run();

        assertThat(calls.get()).isEqualTo(2);
        assertThat(buffer.toString())
                .contains("本轮出错: boom")
                .contains("我 > 坏句")
                .contains("我 > 好句");
    }

    @Test
    void clearSendsEscapeSequenceWhenAnsiEnabled() {
        new ReplLoop(port(true, false), msg -> {
        }, input("/clear\n/exit\n"), "s1").run();

        assertThat(buffer.toString()).contains("\033[2J\033[H");
    }

    @Test
    void clearDoesNotEmitEscapeWhenAnsiDisabled() {
        new ReplLoop(port(false, false), msg -> {
        }, input("/clear\n/exit\n"), "s1").run();

        assertThat(buffer.toString()).doesNotContain("\033");
    }

    @Test
    void endsOnEof() {
        List<String> ran = new ArrayList<>();
        new ReplLoop(port(false, true), ran::add, input("一句"), "s1").run();

        assertThat(ran).containsExactly("一句");
    }

    @Test
    void ignoresBlankLines() {
        List<String> ran = new ArrayList<>();
        new ReplLoop(port(false, true), ran::add, input("\n  \nhi\n/exit\n"), "s1").run();

        assertThat(ran).containsExactly("hi");
    }
}