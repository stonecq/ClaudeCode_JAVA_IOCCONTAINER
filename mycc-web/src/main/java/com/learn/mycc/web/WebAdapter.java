package com.learn.mycc.web;

import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.storage.config.ConfigDefaults;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.ui.UiAdapter;

/**
 * Web UI 适配器：以 Spring Boot（内嵌 Tomcat + SSE）驱动对话。
 * 实现 {@link UiAdapter}，由 {@code Main} 经 SPI 发现并启动；端口取 {@code --port} 或配置 {@code web.port}。
 */
public final class WebAdapter implements UiAdapter {

    @Override
    public String id() {
        return "web";
    }

    @Override
    public void start(IocContainer container, String[] args) {
        Integer port = parsePort(args);
        if (port == null) {
            port = Integer.parseInt(container.getBean(ConfigService.class)
                    .get(ConfigDefaults.WEB_PORT).orElse("8080"));
        }
        System.out.println("Mycc Web 启动中：http://localhost:" + port + "（Ctrl+C 退出）");
        // SpringApplication.run 启动 Tomcat 后即返回，阻塞主线程以保进程存活
        WebApplication.launch(container, port);
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