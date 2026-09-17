package com.learn.mycc.web;

import com.learn.mycc.ui.AgentApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

/**
 * Web 启动器（Spring Boot）：只承载 Web 层（嵌入 Tomcat + Controller + SSE）。
 * <p>agent 门面 {@link AgentApi} 与输出端口 {@link WebPort} 作为 Spring 单例注册，供
 * {@link WebController} 注入——UI 与 agent 完全解耦。</p>
 */
@SpringBootApplication
public class WebApplication {

    /**
     * 启动 Web 服务。
     *
     * @param agent   agent 门面（业务入口）
     * @param webPort 该 UI 的输出端口（SSE 订阅表）
     * @param port    HTTP 端口
     */
    public static void launch(AgentApi agent, WebPort webPort, int port) {
        SpringApplication app = new SpringApplication(WebApplication.class);
        app.setDefaultProperties(Map.of("server.port", String.valueOf(port)));
        app.addInitializers(context -> {
            context.getBeanFactory().registerSingleton("agentApi", agent);
            context.getBeanFactory().registerSingleton("webPort", webPort);
        });
        app.run();
    }
}