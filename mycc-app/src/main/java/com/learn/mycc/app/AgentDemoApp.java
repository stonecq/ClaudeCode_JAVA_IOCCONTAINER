package com.learn.mycc.app;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Session;
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
 * <p>
 * 职责边界：本类只编排「装配 + 交互循环」，可复用逻辑委托给
 * MyccApplication / ConsolePort / SessionMenu，保持 main 方法清晰可读。
 */
public final class AgentDemoApp {

    private AgentDemoApp() {
    }

    /**
     * 应用入口：装配容器 → 选择/新建会话 → 循环读取用户输入驱动 agent 对话。
     * <p>
     * 流程：无历史直接新建会话；有历史则弹 SessionMenu 选择并打印历史后再续聊。
     * 每轮输入先判退出/空行，再交给 {@link AgentLoop}；退出命令与 Ctrl+D 均结束循环，
     * finally 中关闭容器释放资源。
     *
     * @param args 命令行参数（当前未使用，留待 M6 正式 CLI 解析）
     * @throws IOException 标准输入读取或标准输出写入发生 I/O 错误时抛出
     */
    public static void main(String[] args) throws IOException {
        // 显式指定 UTF-8 并启用自动刷新（autoFlush=true），保证中文与流式输出正确
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        // 按基础包装配 IOC 容器，供下方获取 ToolRegistry 等组件
        MyccApplication application = new MyccApplication("com.learn.mycc");
        IocContainer container = application.getIocContainer();
        try {
            out.println("mycc 简易 Agent 演示（deepseek LLM）— 已装配 " + container.getToolRegistry().getAll().size() + " 个工具");
            out.println("/exit 退出");
            // 优先接入 openCode 兼容网关；provider 为空说明缺少 API key，直接结束不进入对话
            OpenAiCompatProvider provider = openCodeDsProvider(out);
            if (provider == null) {
                return;
            }
            // 会话存储在默认目录；有无历史决定是直接新建还是弹选择菜单
            SessionStore store = new SessionStore(FileStorage.defaultDirectory());
            Session session;
            if (store.list().isEmpty()) {
                // 没有任何历史会话：直接新建，跳过菜单交互
                session = Session.create();
            } else {
                // 存在历史会话：弹菜单选择（含「新的对话」），选中后打印历史再续聊
                SessionMenu menu = new SessionMenu(store, out, reader);
                session = menu.select();
                menu.printHistory(session);
            }
            // 从配置读取是否展示推理过程，默认展示；解析失败时回落为 true
            boolean showReasoning = Boolean.parseBoolean(new ConfigService().get("showReasoning", "true"));
            AgentLoop agent = AgentLoop.withToolRegistry(
                    new ConsolePort(out, showReasoning),
                    provider,
                    container.getToolRegistry(),
                    "deepseek-v4-flash", // 使用的 LLM 模型名
                    10,                  // 单轮允许工具调用的最大次数上限（防死循环）
                    store,
                    session);
            while (true) {
                out.print("你 > ");
                out.flush();
                String line = reader.readLine();
                if (line == null) {
                    // 用户按 Ctrl+D：视为结束对话
                    break;
                }
                line = line.trim();
                if (line.equals("/exit")) {
                    // 输入退出命令，结束整个对话循环
                    break;
                }
                if (line.isEmpty()) {
                    // 空输入跳过，不给模型发空消息
                    continue;
                }
                agent.run(line);
                // 每轮结束后换行，让下一个 "你 > " 提示符另起一行
                out.println();
            }
            out.println("bye");
        } finally {
            container.close();
        }
    }


    /**
     * 依据环境变量 OPENCODE_KEY 构造 openCode 兼容网关的 provider。
     * <p>
     * 边界处理：key 缺失或空白时在控制台给出引导提示并返回 null（无法接入），
     * 由调用方据此决定是否结束；key 不写入日志与代码，仅存于进程环境。
     *
     * @param out 提示信息输出流，不可为 null
     * @return 已配置的 provider；API key 缺失时返回 null
     */
    private static OpenAiCompatProvider openCodeDsProvider(PrintStream out) {
        String apiKey = System.getenv("OPENCODE_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            out.println("未设置环境变量 OPENCODE_KEY，无法接入 openCode。");
            out.println("示例：OPENCODE_KEY=sk-xxx mvn -pl mycc-app exec:java");
            return null;
        }
        // openCode 兼容网关基址，LLM 请求默认打到该地址
        String baseUrl = "https://opencode.ai/zen/go/v1";
        return new OpenAiCompatProvider(new ModelConfig(apiKey, baseUrl));
    }

    /**
     * 依据环境变量 DEEPSEEK_API_KEY 构造 DeepSeek 官方接口的 provider。
     * <p>
     * 与 openCodeDsProvider 对称，仅供切换接入目标时选用（当前主流程用 openCode 网关）。
     *
     * @param out 提示信息输出流，不可为 null
     * @return 已配置的 provider；API key 缺失时返回 null
     */
    private static OpenAiCompatProvider deepseekProvider(PrintStream out) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            out.println("未设置环境变量 DEEPSEEK_API_KEY，无法接入 DeepSeek。");
            out.println("示例：DEEPSEEK_API_KEY=sk-xxx mvn -pl mycc-app exec:java");
            return null;
        }
        // DeepSeek 官方接口基址，LLM 请求默认打到该地址
        String baseUrl = "https://api.deepseek.com";
        return new OpenAiCompatProvider(new ModelConfig(apiKey, baseUrl));
    }
}
