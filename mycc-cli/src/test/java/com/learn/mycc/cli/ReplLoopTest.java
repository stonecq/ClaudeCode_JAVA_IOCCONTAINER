package com.learn.mycc.cli;

import com.learn.mycc.cli.ReplLoop.LineInput;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

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

    /** 记录驱动与斜杠命令的假宿主。 */
    static final class FakeHost implements ReplLoop.Host {
        final List<String> turns = new ArrayList<>();
        final List<String> slashes = new ArrayList<>();
        RuntimeException turnError;

        @Override
        public String sessionId() {
            return "s1";
        }

        @Override
        public void runTurn(String userMessage) {
            turns.add(userMessage);
            if (turnError != null) {
                throw turnError;
            }
        }

        @Override
        public void handleSlashCommand(String line) {
            slashes.add(line);
        }
    }

    @Test
    void exitsOnSlashExitCommand() {
        FakeHost host = new FakeHost();
        new ReplLoop(port(false, true), host, input("/exit\n")).run();

        assertThat(host.turns).isEmpty();
    }

    @Test
    void drivesHostForEachUserLine() {
        FakeHost host = new FakeHost();
        new ReplLoop(port(false, true), host, input("第一句\n/exit\n")).run();

        assertThat(host.turns).containsExactly("第一句");
    }

    @Test
    void toleratesRoundExceptionAndContinues() {
        FakeHost host = new FakeHost();
        host.turnError = new IllegalStateException("boom");
        // 每轮都抛，但循环不应中断：两轮都被驱动
        new ReplLoop(port(false, true), host, input("坏句\n好句\n/exit\n")).run();

        assertThat(host.turns).containsExactly("坏句", "好句");
        assertThat(buffer.toString()).contains("本轮出错: boom");
    }

    @Test
    void clearSendsEscapeSequenceWhenAnsiEnabled() {
        new ReplLoop(port(true, false), new FakeHost(), input("/clear\n/exit\n")).run();

        assertThat(buffer.toString()).contains("\033[2J\033[H");
    }

    @Test
    void clearDoesNotEmitEscapeWhenAnsiDisabled() {
        new ReplLoop(port(false, false), new FakeHost(), input("/clear\n/exit\n")).run();

        assertThat(buffer.toString()).doesNotContain("\033");
    }

    @Test
    void endsOnEof() {
        FakeHost host = new FakeHost();
        new ReplLoop(port(false, true), host, input("一句")).run();

        assertThat(host.turns).containsExactly("一句");
    }

    @Test
    void ignoresBlankLines() {
        FakeHost host = new FakeHost();
        new ReplLoop(port(false, true), host, input("\n  \nhi\n/exit\n")).run();

        assertThat(host.turns).containsExactly("hi");
    }

    @Test
    void dispatchesUnknownSlashCommandsToHost() {
        FakeHost host = new FakeHost();
        new ReplLoop(port(false, true), host, input("/sessions\n/resume x\n/exit\n")).run();

        assertThat(host.slashes).containsExactly("/sessions", "/resume x");
        assertThat(host.turns).isEmpty();
    }
}