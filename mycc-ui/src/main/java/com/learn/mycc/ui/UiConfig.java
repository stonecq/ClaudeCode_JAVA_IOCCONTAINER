package com.learn.mycc.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UI 配置：读 {@code ~/.mycc/config.json} 的 {@code ui} 段（与 agent 域分离）。
 * <p>只取 {@code ui.*} key，缺失回落到内置默认；由 UI 侧自持（适配器惰性创建），
 * <b>不经过 {@link AgentApi} 或容器</b>——于是 UI 构建端口不再依赖 agent。</p>
 */
public final class UiConfig {

    /** UI 域 key 前缀。 */
    private static final String UI_PREFIX = "ui.";
    /** CLI 是否渲染思考块。 */
    private static final String CLI_SHOW_REASONING = "ui.cli.showReasoning";
    /** Web 监听端口。 */
    private static final String WEB_PORT = "ui.web.port";
    private static final String DEFAULT_SHOW_REASONING = "true";
    private static final String DEFAULT_WEB_PORT = "8080";

    private final Map<String, String> values = new LinkedHashMap<>();

    /** 读默认配置位置（{@code ~/.mycc/config.json}）。 */
    public UiConfig() {
        this(Path.of(System.getProperty("user.home"), ".mycc", "config.json"));
    }

    /** 读指定配置文件（测试/自定义）；不存在或读取失败时用内置默认。 */
    public UiConfig(Path configFile) {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(configFile.toFile());
            flatten("", root);
        } catch (IOException e) {
            // 读取失败不阻断 UI：用内置默认
        }
    }

    /** @return CLI 是否渲染思考块（{@code ui.cli.showReasoning}）。 */
    public boolean cliShowReasoning() {
        return Boolean.parseBoolean(values.getOrDefault(CLI_SHOW_REASONING, DEFAULT_SHOW_REASONING));
    }

    /** @return Web 监听端口（{@code ui.web.port}）。 */
    public int webPort() {
        return Integer.parseInt(values.getOrDefault(WEB_PORT, DEFAULT_WEB_PORT));
    }

    /** 展平 JSON，仅保留 {@code ui.*} key。 */
    private void flatten(String prefix, JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    flatten(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue()));
        } else if (prefix.startsWith(UI_PREFIX)) {
            values.put(prefix, node.asText());
        }
    }
}