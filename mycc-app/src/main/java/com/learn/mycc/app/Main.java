package com.learn.mycc.app;

import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.UiAdapter;

import java.util.List;
import java.util.ServiceLoader;

/**
 * 启动器：装配自研容器（agent 领域）→ 按 {@code --ui <id>} 经 SPI 选择 UI 适配器 →
 * 取 agent 门面 {@link AgentApi} → 在 {@code start()} 前把适配器提供的两个外向端口（输出 / 审批）
 * 登记进容器 → 交适配器启动。
 * <p>自身不认识任何具体 UI，也不向 UI 暴露容器；新增界面只需加模块 + SPI 注册。</p>
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
            UiAdapter adapter = loadAdapter(uiId);
            // 外向端口先登记（AgentApi 装配时注入到它），再 start 预创建单例
            container.registerSingleton(InteractionPort.class, adapter.port());
            container.registerSingleton(UserConfirmation.class, adapter.userConfirmation());
            application.start();

            AgentApi agent = container.getBean(AgentApi.class);
            adapter.start(agent, uiArgs);

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