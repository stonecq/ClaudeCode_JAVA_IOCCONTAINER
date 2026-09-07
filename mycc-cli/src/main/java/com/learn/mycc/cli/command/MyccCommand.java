package com.learn.mycc.cli.command;

import com.learn.mycc.agent.session.Session;
import com.learn.mycc.cli.CliContext;
import com.learn.mycc.core.annotation.Component;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * picocli 根命令：裸 {@code mycc} 总是新建会话、不回放历史，直接进入 REPL。
 */
@Command(name = "mycc", mixinStandardHelpOptions = true,
        description = "mycc 会话 CLI：裸命令新建会话进入交互；resume/sessions/tools/config 见子命令")
@Component
public final class MyccCommand implements Callable<Integer> {

    private final CliContext ctx;

    public MyccCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Integer call() {
        ctx.enterRepl(Session.create(), false).run();
        return 0;
    }
}