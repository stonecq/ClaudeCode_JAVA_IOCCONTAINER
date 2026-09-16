package com.learn.mycc.storage.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.exception.MyccException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 配置服务：读取 {@code ~/.mycc/config.json}（嵌套 JSON）并提供覆盖优先级：
 * 系统属性 {@code mycc.<key>} &gt; 环境变量 {@code MYCC_<KEY>} &gt; 配置文件 &gt;
 * {@link ConfigDefaults} 内置默认。
 *
 * <p>key 一律点号命名空间（如 {@code agent.model}、{@code compact.resultBudget}）；
 * 文件里的嵌套结构在读入时展平为点号 key（{@code {"agent":{"model":"x"}}} → {@code agent.model=x}）。
 * 首次运行（文件不存在）时按 {@link ConfigDefaults} 生成一份默认 {@code config.json} 供用户修改；
 * 生成失败不阻断启动（仍可用内置默认）。</p>
 */
@Component
public final class ConfigService {

    /** 默认配置文件文件名（位于 {@code ~/.mycc/} 目录下）。 */
    public static final String DEFAULT_CONFIG_FILE = "config.json";
    /** 系统属性前缀，形如 {@code -Dmycc.<key>=value}。 */
    private static final String PROPERTY_PREFIX = "mycc.";
    /** 环境变量前缀，形如 {@code MYCC_<KEY>}。 */
    private static final String ENV_PREFIX = "MYCC_";

    /** 配置文件路径（构造时确定）。 */
    private final Path configFile;
    /** 从配置文件展平后的 key→value；读取时按优先级回落到系统属性/环境变量/内置默认。 */
    private final Map<String, String> fileValues = new LinkedHashMap<>();
    /** JSON 解析/生成器。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /** 使用默认配置位置（{@code ~/.mycc/config.json}）创建配置服务。 */
    public ConfigService() {
        this(Path.of(System.getProperty("user.home"), ".mycc", DEFAULT_CONFIG_FILE));
    }

    /**
     * 从指定路径加载配置；文件不存在时按默认项生成一份，生成失败不阻断。
     *
     * @param configFile 配置文件路径
     * @throws MyccException 文件存在但解析失败时抛出
     */
    public ConfigService(Path configFile) {
        this.configFile = configFile;
        writeDefaultFileIfMissing();
        loadFile();
    }

    /**
     * 读取配置值。
     *
     * @param key 配置项名称（点号命名空间，无 {@code mycc.} 前缀）
     * @return 命中值（系统属性 &gt; 环境变量 &gt; 配置文件 &gt; 内置默认）；均未命中返回空
     */
    public Optional<String> get(String key) {
        String systemProperty = System.getProperty(PROPERTY_PREFIX + key);
        if (systemProperty != null) {
            return Optional.of(systemProperty);
        }
        String env = System.getenv(envName(key));
        if (env != null) {
            return Optional.of(env);
        }
        String fileValue = fileValues.get(key);
        if (fileValue != null) {
            return Optional.of(fileValue);
        }
        return Optional.ofNullable(ConfigDefaults.of(key));
    }

    /**
     * 读取配置值，未命中（含无内置默认）时返回默认值。
     *
     * @param key          配置项名称
     * @param defaultValue 兜底默认值
     * @return 命中值或 defaultValue
     */
    public String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /** 读取字符串配置值（含内置默认；均未命中时抛 {@link MyccException}）。 */
    public String getString(String key) {
        return require(key);
    }

    /** @return int 配置值（内置默认见 {@link ConfigDefaults}）。 */
    public int getInt(String key) {
        return Integer.parseInt(require(key));
    }

    /** @return boolean 配置值。 */
    public boolean getBool(String key) {
        return Boolean.parseBoolean(require(key));
    }

    /** @return double 配置值。 */
    public double getDouble(String key) {
        return Double.parseDouble(require(key));
    }

    /** @return 配置文件路径（供 CLI 展示）。 */
    public Path file() {
        return configFile;
    }

    private String require(String key) {
        return get(key).orElseThrow(() -> new MyccException("未配置且无默认值: " + key));
    }

    /** 文件不存在时按 {@link ConfigDefaults} 生成默认 config.json；失败静默（仍可用内置默认）。 */
    private void writeDefaultFileIfMissing() {
        if (Files.isRegularFile(configFile)) {
            return;
        }
        try {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), nestedDefaults());
        } catch (IOException e) {
            // 生成失败不阻断启动：无文件时读取仍回落到 ConfigDefaults
        }
    }

    private Map<String, Object> nestedDefaults() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ConfigDefaults.all().entrySet()) {
            putNested(root, entry.getKey().split("\\."), entry.getValue());
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void putNested(Map<String, Object> node, String[] path, String value) {
        Map<String, Object> current = node;
        for (int i = 0; i < path.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(path[i], k -> new LinkedHashMap<String, Object>());
        }
        current.put(path[path.length - 1], typed(value));
    }

    /** 默认值字符串尽量还原为 JSON 原生类型（int/double/bool），否则字符串。 */
    private static Object typed(String value) {
        if ("true".equals(value) || "false".equals(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // 非整数，尝试浮点
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private void loadFile() {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(configFile.toFile());
            flatten("", root, fileValues);
        } catch (IOException e) {
            throw new MyccException("加载配置文件失败: " + configFile + " / " + e.getMessage(), e);
        }
    }

    /** 把嵌套 JSON 展平为点号 key（叶子节点的文本值）。 */
    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    flatten(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue(), out));
        } else {
            out.put(prefix, node.asText());
        }
    }

    /** key → 环境变量名：{@code .} 与驼峰边界转 {@code _} 并大写，如 {@code agent.maxIterations} → {@code MYCC_AGENT_MAX_ITERATIONS}。 */
    static String envName(String key) {
        StringBuilder sb = new StringBuilder(ENV_PREFIX);
        for (char c : key.toCharArray()) {
            if (c == '.') {
                sb.append('_');
            } else if (Character.isUpperCase(c)) {
                sb.append('_').append(c);
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }
}