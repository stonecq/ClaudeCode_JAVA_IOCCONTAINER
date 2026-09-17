package com.learn.mycc.web;

import com.learn.mycc.core.permission.UnavailableUserConfirmation;
import com.learn.mycc.core.permission.UserConfirmation;
import com.learn.mycc.storage.config.ConfigDefaults;
import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.InteractionPort;
import com.learn.mycc.ui.UiAdapter;

/**
 * Web UI 适配器：以 Spring Boot（内嵌 Tomcat + SSE）驱动对话（{@code --ui web}）。
 * <p>输出端口为 {@link WebPort}；审批端口暂用 {@link UnavailableUserConfirmation}（fail-closed，
 * Web 交互式审批后续再实现）。端口取 {@code --port} 或配置 {@code web.port}；只经 {@link AgentApi}
 * 与 agent 交互。</p>
 */
public final class WebAdapter implements UiAdapter {

    private WebPort port;
    private UserConfirmation confirm;

    @Override
    public String id() {
        return "web";
    }

    @Override
    public InteractionPort port(AgentApi agent) {
        if (port == null) {
            port = new WebPort();
        }
        return port;
    }

    @Override
    public UserConfirmation userConfirmation(AgentApi agent) {
        if (confirm == null) {
            confirm = new UnavailableUserConfirmation();
        }
        return confirm;
    }

    @Override
    public void start(AgentApi agent, String[] args) {
        port(agent); // 确保输出端口就绪（Main 已登记同一实例）
        Integer listenPort = parsePort(args);
        if (listenPort == null) {
            listenPort = Integer.parseInt(agent.config(ConfigDefaults.WEB_PORT));
        }
        System.out.println("Mycc Web 启动中：http://localhost:" + listenPort + "（Ctrl+C 退出）");
        // SpringApplication.run 启动 Tomcat 后即返回，阻塞主线程以保进程存活
        WebApplication.launch(agent, port, listenPort);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 解析 {@code --port N}；未给出返回 null（回落到配置）。 */
    private static Integer parsePort(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--port".equals(args[i])) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return null;
    }
}