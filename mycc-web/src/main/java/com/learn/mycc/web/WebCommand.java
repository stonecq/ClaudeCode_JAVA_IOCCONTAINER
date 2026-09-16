package com.learn.mycc.web;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.storage.config.ConfigDefaults;
import com.learn.mycc.storage.config.ConfigService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * {@code mycc web}：启动 Web 界面。
 * <p>把 agent 的 {@link InteractionPort} 从 CLI 端口换成 {@link WebPort}，再启动 Spring Boot Web
 * （阻塞至进程结束）。端口优先取 {@code --port}，其次配置 {@code web.port}，最后 Spring 默认 8080。</p>
 */
@Command(name = "web", mixinStandardHelpOptions = true, description = "启动 Web 界面（浏览器访问 http://localhost:<port>）")
@Component
public final class WebCommand implements Callable<Integer> {

    @Option(names = "--port", description = "监听端口（默认取配置 web.port）")
    private Integer port;

    private final IocContainer container;
    private final ConfigService config;

    @Inject
    public WebCommand(IocContainer container, ConfigService config) {
        this.container = container;
        this.config = config;
    }

    @Override
    public Integer call() {
        Integer effectivePort = port != null ? port
                : Integer.parseInt(config.get(ConfigDefaults.WEB_PORT).orElse("8080"));
        System.out.println("Mycc Web 启动中：http://localhost:" + effectivePort + "（Ctrl+C 退出）");
        // launch 内部把 InteractionPort 换成 WebPort 并启动 Spring Boot（非阻塞返回）
        WebApplication.launch(container, effectivePort);
        // SpringApplication.run 启动 Tomcat 后即返回，这里阻塞主线程，避免 Main 走到 System.exit 关掉进程
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }
}