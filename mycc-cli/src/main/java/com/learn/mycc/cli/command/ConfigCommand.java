package com.learn.mycc.cli.command;

import com.learn.mycc.cli.CliContext;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.storage.config.ConfigDefaults;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/** {@code config}：打印当前已知配置项的生效值（当前仅 {@code showReasoning}）。 */
@Command(name = "config", mixinStandardHelpOptions = true, description = "打印配置生效值（当前仅 showReasoning）")
@Component
public final class ConfigCommand implements Callable<Integer> {

    private final CliContext ctx;

    public ConfigCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Integer call() {
        ctx.out().println(ConfigDefaults.CLI_SHOW_REASONING + " = " + ctx.config().get(ConfigDefaults.CLI_SHOW_REASONING).orElse(""));
        return 0;
    }
}