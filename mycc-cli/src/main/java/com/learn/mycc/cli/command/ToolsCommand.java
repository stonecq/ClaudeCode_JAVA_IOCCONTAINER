package com.learn.mycc.cli.command;

import com.learn.mycc.cli.CliContext;
import com.learn.mycc.core.tool.ToolDefinition;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/** {@code tools}：列出已注册工具（名称 + 描述）。 */
@Command(name = "tools", mixinStandardHelpOptions = true, description = "列出已注册工具（名称 + 描述）")
public final class ToolsCommand implements Callable<Integer> {

    private final CliContext ctx;

    public ToolsCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Integer call() {
        for (ToolDefinition tool : ctx.registry().getAll()) {
            ctx.out().println(tool.getName() + "\t" + tool.getDescription());
        }
        return 0;
    }
}