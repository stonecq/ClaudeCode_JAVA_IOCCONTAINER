package com.learn.mycc.cli.command;

import com.learn.mycc.cli.CliContext;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/** {@code config}：打印当前已知配置项的生效值（当前仅 {@code showReasoning}）。 */
@Command(name = "config", mixinStandardHelpOptions = true, description = "打印配置生效值（当前仅 showReasoning）")
public final class ConfigCommand implements Callable<Integer> {

    private final CliContext ctx;

    public ConfigCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Integer call() {
        ctx.out().println("showReasoning = " + ctx.config().get("showReasoning", "true"));
        return 0;
    }
}