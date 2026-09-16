package com.learn.mycc.app;

import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.UiAdapter;
import com.learn.mycc.web.WebPort;

import java.util.List;
import java.util.ServiceLoader;

/**
 * 启动器：装配自研容器（agent 领域）→ 按 {@code --ui <id>} 经 SPI 选择 UI 适配器 → 交由适配器启动。
 * 自身不承载具体 UI 逻辑（CLI 的子命令、Web 的 Spring 都在各自适配器里）；新增界面只需加模块 + SPI 注册。
 */
public final class Main {

    /** 未指定 {@code --ui} 时的默认 UI。 */
    private static final String DEFAULT_UI = "cli";

    private Main() {
    }

    public static void main(String[] args) {
        String uiId = uiId(args);
        String[] uiArgs = stripUi(args);

        MyccApplication application = new MyccApplication();
        IocContainer container = application.getIocContainer();
        try {
            // 全部 bean 在 start 前注册完毕：web 模式把 InteractionPort 注册为 WebPort（唯一键）。
            // （"由 UI 提供外向端口"待 CLI 去容器化阶段一并移入 UiAdapter。）
            if ("web".equals(uiId)) {
                WebPort webPort = new WebPort();
                container.registerSingleton(WebPort.class, webPort);
                container.registerSingleton(InteractionPort.class, webPort);
            }
            application.start();

            UiAdapter adapter = loadAdapter(uiId);
            adapter.start(container, uiArgs);

            container.close();
            System.exit(0);
        } finally {
            // 异常/提前退出兜底；close 幂等
            container.close();
        }
    }

    /** 解析 {@code --ui <id>}；缺省 {@link #DEFAULT_UI}。 */
    private static String uiId(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--ui".equals(args[i])) {
                return args[i + 1];
            }
        }
        return DEFAULT_UI;
    }

    /** 剥离 {@code --ui <id>} 两个 token，其余参数交给 UI 适配器。 */
    private static String[] stripUi(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--ui".equals(args[i])) {
                String[] rest = new String[args.length - 2];
                System.arraycopy(args, 0, rest, 0, i);
                System.arraycopy(args, i + 2, rest, i, args.length - i - 2);
                return rest;
            }
        }
        return args;
    }

    /** 经 SPI 找到 id 匹配的 UI 适配器。 */
    private static UiAdapter loadAdapter(String id) {
        List<UiAdapter> adapters = ServiceLoader.load(UiAdapter.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        return adapters.stream()
                .filter(adapter -> adapter.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "未知的 UI: " + id + "（可用: " + adapters.stream().map(UiAdapter::id).toList() + "）"));
    }
}