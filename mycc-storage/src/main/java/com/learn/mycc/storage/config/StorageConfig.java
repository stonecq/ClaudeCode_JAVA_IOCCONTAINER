package com.learn.mycc.storage.config;

import com.learn.mycc.core.annotation.Bean;
import com.learn.mycc.core.annotation.Configuration;
import com.learn.mycc.storage.file.FileStorage;

/**
 * 存储装配配置：把 {@link FileStorage} 以 @Bean 工厂方法交给容器纳管。
 * 供 {@code Storage} 接口可匹配落实现（唯一候选），或按 {@code FileStorage} 类型直接注入。
 */
@Configuration
public class StorageConfig {

    /** 默认根目录（{@code ~/.mycc/}）的文件存储实现。 */
    @Bean
    public FileStorage fileStorage() {
        return FileStorage.defaultDirectory();
    }
}