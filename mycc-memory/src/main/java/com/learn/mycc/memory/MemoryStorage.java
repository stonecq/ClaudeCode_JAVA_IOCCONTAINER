package com.learn.mycc.memory;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.storage.file.FileStorage;
import com.learn.mycc.storage.spi.Storage;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class MemoryStorage {
    private final Storage storage;

    @Inject
    public MemoryStorage(FileStorage storage) {
        this.storage = storage;
    }

    private final String MEMORY_PREFIX = "memory/";

    private final String USER_PREFIX = "user/";

    private final String PROJECT_PREFIX = "project/";

    private final String SESSION_PREFIX = "session/";

    // 编码：将路径转为安全的文件夹名
    public static String encodeProjectPath(String path) {
        if (path == null) return null;
        // 使用 UTF-8 编码，将特殊字符转为 %XX 格式
        return URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    // 解码：将文件夹名还原为原始路径
    public static String decodeProjectPath(String encodedPath) {
        if (encodedPath == null) return null;
        // 还原回原始路径
        return URLDecoder.decode(encodedPath, StandardCharsets.UTF_8);
    }

    public void saveMemory(String content, String id,MemoryType type){
        String key = key(id, type);
        storage.write(key, content);
    }

    private String key(String key, MemoryType type){
        return switch (type){
            case USER -> MEMORY_PREFIX + USER_PREFIX + key + ".json";
            case PROJECT -> MEMORY_PREFIX + PROJECT_PREFIX + encodeProjectPath(key) + ".json";
            case SESSION -> MEMORY_PREFIX + SESSION_PREFIX + key + ".json";
        };
    }

    public String loadMemory(String id, MemoryType type){
        Optional<String> result = storage.read(key(id, type));
        return result.orElse(null);
    }
}
