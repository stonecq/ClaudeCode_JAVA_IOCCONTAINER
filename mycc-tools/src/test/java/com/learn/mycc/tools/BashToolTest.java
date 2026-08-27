package com.learn.mycc.tools;

import com.learn.mycc.core.exception.MyccException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BashToolTest {

    private final BashTool tool = new BashTool();

    @Test
    void executesCommandAndReturnsStdout() {
        BashResult result = tool.bash("echo hello");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("hello");
    }

    @Test
    void separatesStderr() {
        BashResult result = tool.bash("echo oops 1>&2");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).doesNotContain("oops");
        assertThat(result.stderr()).contains("oops");
    }

    @Test
    void returnsNonZeroExitCode() {
        BashResult result = tool.bash("exit 3");
        assertThat(result.exitCode()).isEqualTo(3);
    }

    @Test
    void rejectsBlankCommand() {
        assertThatThrownBy(() -> tool.bash("   "))
                .isInstanceOf(MyccException.class)
                .hasMessageContaining("不能为空");
    }
}
