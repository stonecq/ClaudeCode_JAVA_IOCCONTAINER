package com.learn.mycc.app;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ModelConfig;
import com.learn.mycc.ai.provider.OpenAiCompatProvider;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.storage.file.FileStorage;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * M4 交互式演示入口：真实 DeepSeek LLM 驱动工具调用的迷你 agent 会话。
 * 正式 CLI（JLine/picocli/ANSI）在 M6 实现。
 */
public final class AgentDemoApp {

    private AgentDemoApp() {
    }

    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        MyccApplication application = new MyccApplication("com.learn.mycc");
        IocContainer container = application.getIocContainer();
        try {
            out.println("mycc 简易 Agent 演示（deepseek LLM）— 已装配 " + container.getToolRegistry().getAll().size() + " 个工具");
            out.println("/exit 退出");
            OpenAiCompatProvider provider = openCodeDsProvider(out);
            if (provider == null) {
                return;
            }
            SessionStore store = new SessionStore(FileStorage.defaultDirectory());
            if (store.latest().isPresent()) {
                out.println("检测到历史会话，将自动恢复继续上次上下文。");
            }
            boolean showReasoning = Boolean.parseBoolean(new ConfigService().get("showReasoning", "true"));
            AgentLoop agent = AgentLoop.withToolRegistry(
                    new ConsolePort(out, showReasoning),
                    provider,
                    container.getToolRegistry(),
                    "deepseek-v4-flash",
                    10,
                    store);
            while (true) {
                out.print("你 > ");
                out.flush();
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.equals("/exit")) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                agent.run(line);
                out.println();
            }
            out.println("bye");
        } finally {
            container.close();
        }
    }


    private static OpenAiCompatProvider openCodeDsProvider(PrintStream out) {
        String apiKey = System.getenv("OPENCODE_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            out.println("未设置环境变量 OPENCODE_KEY，无法接入 openCode。");
            out.println("示例：OPENCODE_KEY=sk-xxx mvn -pl mycc-app exec:java");
            return null;
        }
        String baseUrl = "https://opencode.ai/zen/go/v1";
        return new OpenAiCompatProvider(new ModelConfig(apiKey, baseUrl));
    }

    private static OpenAiCompatProvider deepseekProvider(PrintStream out) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            out.println("未设置环境变量 DEEPSEEK_API_KEY，无法接入 DeepSeek。");
            out.println("示例：DEEPSEEK_API_KEY=sk-xxx mvn -pl mycc-app exec:java");
            return null;
        }
        String baseUrl = "https://api.deepseek.com";
        return new OpenAiCompatProvider(new ModelConfig(apiKey, baseUrl));
    }
}
