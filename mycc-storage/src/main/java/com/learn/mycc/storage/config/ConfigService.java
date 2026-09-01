package com.learn.mycc.storage.config;

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
 */
public final class ConfigService {

    public static final String DEFAULT_CONFIG_FILE = "config";
    private static final String PROPERTY_PREFIX = "mycc.";
    private static final String ENV_PREFIX = "MYCC_";

    private final Properties fileProperties = new Properties();

    public ConfigService() {
        this(Path.of(System.getProperty("user.home"), ".mycc", DEFAULT_CONFIG_FILE));
    }

    public ConfigService(Path configFile) {
        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                fileProperties.load(in);
            } catch (IOException e) {
                throw new MyccException("加载配置文件失败: " + configFile + " / " + e.getMessage(), e);
            }
        }
    }

    public String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

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
