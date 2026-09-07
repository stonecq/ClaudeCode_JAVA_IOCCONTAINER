package com.learn.mycc.storage.config;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.exception.MyccException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * 配置服务：加载 {@code ~/.mycc/config}（key=value 属性文件）并提供覆盖优先级：
 * 系统属性 {@code mycc.<key>} > 环境变量 {@code MYCC_<KEY>} > 用户配置文件 > 内置默认值。
 *
 * <p>设计思路：启动时只读一次性加载属性文件（启动后变更不感知，简单够用）；
 * 提供多层覆盖是为了兼顾"命令行可覆盖、环境注入可覆盖、落到硬盘可持久化"三种
 * 常见部署场景。上层调用 {@link #get(String)} 即可统一读取，无需关心取值来源。</p>
 */
@Component
public final class ConfigService {

    /** 默认配置文件文件名（位于 {@code ~/.mycc/} 目录下）。 */
    public static final String DEFAULT_CONFIG_FILE = "config";
    /** 系统属性前缀，形如 {@code -Dmycc.<key>=value}。 */
    private static final String PROPERTY_PREFIX = "mycc.";
    /** 环境变量前缀，形如 {@code MYCC_<KEY>}，与属性前缀相对应。 */
    private static final String ENV_PREFIX = "MYCC_";

    /** 从用户配置文件加载的 key-value；默认为空，加载后才填充。 */
    private final Properties fileProperties = new Properties();

    /** 使用默认配置位置（{@code ~/.mycc/config}）创建配置服务。 */
    public ConfigService() {
        this(Path.of(System.getProperty("user.home"), ".mycc", DEFAULT_CONFIG_FILE));
    }

    /**
     * 从指定路径加载配置文件；文件不存在时不报错，仅保留默认空配置。
     *
     * @param configFile 配置文件路径；若非普通文件则整体视为无文件配置
     * @throws MyccException 文件存在但解析（读取/格式）失败时抛出
     */
    public ConfigService(Path configFile) {
        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                fileProperties.load(in);
            } catch (IOException e) {
                throw new MyccException("加载配置文件失败: " + configFile + " / " + e.getMessage(), e);
            }
        }
    }

    /**
     * 读取配置值，未命中时返回默认值。
     *
     * @param key          配置项名称，无前缀
     * @param defaultValue 任意来源都未命中时返回的兜底默认值
     * @return 命中值，或 defaultValue
     */
    public String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * 按优先级读取配置值：系统属性 > 环境变量 > 配置文件 > 空。
     *
     * <p>命名映射说明：环境变量把点替换为下划线并大写（如 key 为
     * {@code api.timeout} 时，读取 {@code MYCC_API_TIMEOUT}），这是 Unix 环境变量
     * 不允许带点的通用约定。</p>
     *
     * @param key 配置项名称，无前缀；不应包含点以外的特殊字符
     * @return 命中值包装于 Optional；所有来源均未配置时返回 {@link Optional#empty()}
     */
    public Optional<String> get(String key) {
        String systemProperty = System.getProperty(PROPERTY_PREFIX + key);
        if (systemProperty != null) {
            return Optional.of(systemProperty);
        }
        String env = System.getenv(ENV_PREFIX + key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (env != null) {
            return Optional.of(env);
        }
        String fileValue = fileProperties.getProperty(key);
        if (fileValue != null) {
            return Optional.of(fileValue);
        }
        return Optional.empty();
    }
}
