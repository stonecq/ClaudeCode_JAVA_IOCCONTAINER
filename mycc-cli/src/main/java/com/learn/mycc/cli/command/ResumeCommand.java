package com.learn.mycc.cli.command;

import com.learn.mycc.agent.session.Session;
import com.learn.mycc.cli.CliContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * {@code resume [id]}：续聊历史会话。无 id 续最近一次，有 id 续指定；均先回放历史再进 REPL。
 * 无历史 / id 不存在时提示并返回退出码 1（不自动新建）。
 */
@Command(name = "resume", mixinStandardHelpOptions = true, description = "续聊历史会话：无 id 续最近，有 id 续指定")
public final class ResumeCommand implements Callable<Integer> {

    private final CliContext ctx;

    @Parameters(index = "0", arity = "0..1")
    private String id;

    public ResumeCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Integer call() {
        Optional<Session> loaded;
        if (id == null || id.isBlank()) {
            loaded = ctx.store().latest();
        } else {
            loaded = ctx.store().load(id);
        }
        if (loaded.isEmpty()) {
            ctx.out().println(id == null || id.isBlank() ? "没有历史会话" : "找不到会话 " + id);
            return 1;
        }
        ctx.enterRepl(loaded.get(), true).run();
        return 0;
    }
}