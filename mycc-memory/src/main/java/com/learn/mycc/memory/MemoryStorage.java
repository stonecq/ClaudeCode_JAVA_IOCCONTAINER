package com.learn.mycc.memory;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.storage.file.FileStorage;
import com.learn.mycc.storage.spi.Storage;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 长期记忆门面：在 {@link Storage} 之上提供三层记忆。
 * <p>USER / PROJECT 层是「索引 + 多条目」：每层一个 {@code MEMORY.md} 索引（每行
 * {@code - id: 描述}，供 LLM 快速浏览并按 id 调取全文），条目按 id 各自落盘；
 * PROJECT 层以当前工作区为命名空间隔离不同项目的记忆。SESSION 层保持每会话一条，
 * 由 {@link #loadSession}/{@link #saveSession} 专用方法读写，不走索引
 * （会话是一次性的，不适合拆多条）。</p>
 * <p>id 一律经 {@link #encodeProjectPath} 作文件名，保证任何 LLM 生成的 id 都能安全落盘；
 * 索引文本中的 id 保持原文，供 LLM 阅读与回传。</p>
 */
@Component
public class MemoryStorage {

    private final Storage storage;
    /** 当前工作区字符串，作 PROJECT 层命名空间；USER 层为全局，不受影响。 */
    private final String workspacePath;

    @Inject
    public MemoryStorage(FileStorage storage, ApplicationConfig config) {
        this.storage = storage;
        this.workspacePath = config.getWorkspacePath().toString();
    }

    private static final String MEMORY_PREFIX = "memory/";
    private static final String INDEX_FILE = "MEMORY.md";

    /** 编码：将路径/记忆 id 转为安全的文件夹名（%XX 形式）。 */
    public static String encodeProjectPath(String path) {
        if (path == null) {
            return null;
        }
        return URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    /** 解码：将文件夹名还原为原始路径/id。 */
    public static String decodeProjectPath(String encodedPath) {
        if (encodedPath == null) {
            return null;
        }
        return URLDecoder.decode(encodedPath, StandardCharsets.UTF_8);
    }

    /** 读取某层记忆索引文本；暂无记忆返回 null。仅 USER/PROJECT 可用。 */
    public String loadIndex(MemoryType type) {
        requireEntryType(type);
        return readKey(indexKey(type));
    }

    /** 读取一条记忆全文；不存在返回 null。仅 USER/PROJECT 可用。 */
    public String load(String id, MemoryType type) {
        requireEntryType(type);
        return readKey(entryKey(id, type));
    }

    /** 新增或更新一条记忆：写入条目文件并把索引 upsert 为「id + 描述」。仅 USER/PROJECT 可用。 */
    public void save(String id, String description, String content, MemoryType type) {
        requireEntryType(type);
        validateId(id);
        validateDescription(description);
        storage.write(entryKey(id, type), content);
        String index = loadIndex(type);
        storage.write(indexKey(type), MemoryIndex.upsert(index, id, description));
    }

    /**
     * 删除一条记忆：移除条目文件并清掉索引中对应行；索引清空则连同索引文件一起删除。
     *
     * @return 条目原本存在并被删除返回 true；原本不存在返回 false
     */
    public boolean delete(String id, MemoryType type) {
        requireEntryType(type);
        if (!storage.delete(entryKey(id, type))) {
            return false;
        }
        String updated = MemoryIndex.remove(loadIndex(type), id);
        if (updated.isEmpty()) {
            storage.delete(indexKey(type));
        } else {
            storage.write(indexKey(type), updated);
        }
        return true;
    }

    /** 读取会话层记忆（每会话一条）；无记录返回 null。 */
    public String loadSession(String sessionId) {
        return readKey(sessionKey(sessionId));
    }

    /** 覆盖写会话层记忆。 */
    public void saveSession(String sessionId, String content) {
        storage.write(sessionKey(sessionId), content);
    }

    /** USER/PROJECT 层索引文件 key。 */
    private String indexKey(MemoryType type) {
        return switch (type) {
            case USER -> MEMORY_PREFIX + "user/" + INDEX_FILE;
            case PROJECT -> MEMORY_PREFIX + "project/" + encodeProjectPath(workspacePath) + "/" + INDEX_FILE;
            case SESSION -> throw new MyccException("会话层记忆请用 loadSession/saveSession");
        };
    }

    /** USER/PROJECT 层条目文件 key。 */
    private String entryKey(String id, MemoryType type) {
        return switch (type) {
            case USER -> MEMORY_PREFIX + "user/" + encodeProjectPath(id) + ".md";
            case PROJECT -> MEMORY_PREFIX + "project/" + encodeProjectPath(workspacePath) + "/"
                    + encodeProjectPath(id) + ".md";
            case SESSION -> throw new MyccException("会话层记忆请用 loadSession/saveSession");
        };
    }

    /** SESSION 层单条文件 key。 */
    private String sessionKey(String sessionId) {
        return MEMORY_PREFIX + "session/" + sessionId + ".md";
    }

    private String readKey(String key) {
        Optional<String> result = storage.read(key);
        return result.orElse(null);
    }

    private void requireEntryType(MemoryType type) {
        if (type == MemoryType.SESSION) {
            throw new MyccException("会话层记忆请使用 loadSession/saveSession，不走索引条目");
        }
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new MyccException("记忆 id 不能为空");
        }
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new MyccException("记忆描述不能为空");
        }
        if (description.contains("\n")) {
            throw new MyccException("记忆描述必须为单行");
        }
    }
}