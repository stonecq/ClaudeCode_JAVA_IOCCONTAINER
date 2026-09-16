package com.learn.mycc.web;

import com.learn.mycc.core.context.IocContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

/**
 * Web 启动器（Spring Boot）：只承载 Web 层（嵌入 Tomcat + Controller + SSE）。
 * <p>agent / 工具 / 存储仍在<b>自研 IoC 容器</b>里——{@link #launch} 把该容器注册为 Spring 单例，
 * {@link WebController} 由它取 {@code SessionStore} 并按会话装配 {@code AgentLoop}。两套容器各司其职：
 * Spring 管 HTTP，自研容器管 agent。</p>
 */
@SpringBootApplication
public class WebApplication {

    /**
     * 启动 Web 服务。
     *
     * @param container 已开始的自研 IoC 容器（agent 侧）
     * @param port      HTTP 端口；null 表示用 Spring 默认（8080）
     */
    public static void launch(IocContainer container, Integer port) {
        SpringApplication app = new SpringApplication(WebApplication.class);
        if (port != null) {
            app.setDefaultProperties(Map.of("server.port", String.valueOf(port)));
        }
        // 把自研容器作为单例注册进 Spring，供 WebController 注入
        app.addInitializers(context ->
                context.getBeanFactory().registerSingleton("iocContainer", container));
        app.run();
    }

    /** 独立启动入口（备用）：自建自研容器后启动 Web；生产入口是 {@code mycc web} 子命令。 */
    public static void main(String[] args) {
        IocContainer container = IocContainer.create();
        container.register("com.learn.mycc");
        container.start();
        launch(container, null);
    }
}