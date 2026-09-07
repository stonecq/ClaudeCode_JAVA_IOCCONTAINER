package com.learn.mycc.cli.command;

import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.cli.CliContext;
import com.learn.mycc.core.annotation.Component;
import picocli.CommandLine.Command;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

/** {@code sessions}：列出历史会话（id + 修改时间 + 标题，按最后修改倒序）。 */
@Command(name = "sessions", mixinStandardHelpOptions = true, description = "列出历史会话（id + 修改时间 + 标题）")
@Component
public final class SessionsCommand implements Callable<Integer> {

    private final CliContext ctx;

    public SessionsCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Integer call() {
        List<SessionStore.SessionSummary> list = ctx.store().list();
        if (list.isEmpty()) {
            ctx.out().println("（暂无历史会话）");
            return 0;
        }
        for (SessionStore.SessionSummary summary : list) {
            ctx.out().println(summary.id() + "\t" + Instant.ofEpochMilli(summary.lastModified()) + "\t" + summary.title());
        }
        return 0;
    }
}