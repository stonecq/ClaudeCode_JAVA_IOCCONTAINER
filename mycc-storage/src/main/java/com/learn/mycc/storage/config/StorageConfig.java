package com.learn.mycc.storage.config;

import com.learn.mycc.core.annotation.Bean;
import com.learn.mycc.core.annotation.Configuration;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.storage.file.FileStorage;
import com.learn.mycc.storage.file.WorkspaceStorage;

/**
 * 存储装配配置：把 {@link FileStorage} 与 {@link WorkspaceStorage} 以 @Bean 工厂方法交给容器纳管。
 * 供 {@code Storage} 接口可匹配落实现（唯一候选），或按 {@code FileStorage} 类型直接注入。
 */
@Configuration
public class StorageConfig {

    /** 默认根目录（{@code ~/.mycc/}）的全局文件存储实现。 */
    @Bean
    public FileStorage fileStorage() {
        return FileStorage.defaultDirectory();
    }

    /**
     * 工作区 {@code <workspace>/.mycc} 存储。用独立类型 {@link WorkspaceStorage}
     * （而非再注册一个 FileStorage/Storage）：容器按 Class 注册类型唯一，同类型第二份会冲突。
     */
    @Bean
    public WorkspaceStorage workspaceStorage(ApplicationConfig config) {
        return new WorkspaceStorage(config);
    }
}