package com.learn.mycc.storage.permission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.permission.PermissionDecision;
import com.learn.mycc.core.permission.PermissionRuleStore;
import com.learn.mycc.core.permission.PermissionVerdict;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 权限规则存储：会话内存缓存 + 项目内 JSON 持久化（M8 权限管理的决策持久化）。
 * <p>持久文件为 {@code <workspace>/.mycc/permissions.json}，以 {@code {tool: "VERDICT"}}
 * 形式存放跨会话规则（Jackson 序列化），可人工查看/编辑；会话内存缓存叠加其上，
 * 保证同一会话内已确认的规则不重复读盘。决策优先级：会话内存 &gt; 项目文件 &gt; 风险默认。</p>
 *
 * <p>职责边界：只负责规则存取，不参与决策（决策见 core 的 PermissionPolicy）。
 * SESSION_START 时调用 {@link #clearSession()} 清空内存缓存，避免恢复的旧会话继承
 * 已过期的内存态；持久文件不受影响，跨会话持续生效。</p>
 */
@Component
public final class JsonPermissionRuleStore implements PermissionRuleStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    private ApplicationConfig config;

    /** 当前会话内确认的规则（叠加在持久文件之上，优先解析）。 */
    private final Map<String, PermissionDecision> sessionRules = new HashMap<>();

    public JsonPermissionRuleStore() {}

    public JsonPermissionRuleStore(ApplicationConfig config) {
        this.config = config;
    }

    /**
     * 解析规则：先会话内存，再项目持久文件；均未命中返回 null。
     *
     * @param toolName 工具名
     * @return 命中规则的决策；无规则返回 null
     * @throws MyccException 持久文件存在但解析失败时抛出（fail-fast）
     */
    @Override
    public PermissionDecision resolve(String toolName) {
        if (sessionRules.containsKey(toolName)) {
            return sessionRules.get(toolName);
        }
        return projectRules().get(toolName);
    }

    /**
     * 记录规则：写入会话内存并同步持久化到项目 JSON 文件。
     *
     * @param toolName 工具名
     * @param decision 决策，不允许为 null
     * @throws MyccException 文件写入失败（IOException）时抛出
     */
    @Override
    public void remember(String toolName, PermissionDecision decision) {
        sessionRules.put(toolName, decision);
        Map<String, PermissionDecision> project = new LinkedHashMap<>(projectRules());
        project.put(toolName, decision);
        writeProject(project);
    }

    /** 清空会话内存缓存；持久文件规则不受影响（跨会话仍生效）。 */
    @Override
    public void clearSession() {
        sessionRules.clear();
    }

    /** @return 项目持久规则文件路径：{@code <workspace>/.mycc/permissions.json} */
    private Path permissionsFile() {
        return config.getWorkspacePath().resolve(".mycc").resolve("permissions.json");
    }

    /**
     * 读取磁盘上的项目规则；文件不存在或为空时视为无规则。
     *
     * @throws MyccException 文件存在但非空 JSON 解析失败时抛出
     */
    private Map<String, PermissionDecision> projectRules() {
        Path file = permissionsFile();
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            if (Files.size(file) == 0) {
                // 空文件（如手动 touch 出的）视为空规则，避免 readValue 对空输入抛异常
                return new LinkedHashMap<>();
            }
            Map<String, String> raw = JSON.readValue(file.toFile(), new TypeReference<Map<String, String>>() {});
            Map<String, PermissionDecision> rules = new LinkedHashMap<>();
            raw.forEach((tool, verdict) -> rules.put(tool, toDecision(tool, verdict)));
            return rules;
        } catch (IOException e) {
            throw new MyccException("读取权限规则失败: " + file + " / " + e.getMessage(), e);
        }
    }

    /** 把规则以 tool→verdict 形式覆盖写盘（父目录自动创建）。 */
    private void writeProject(Map<String, PermissionDecision> rules) {
        Path file = permissionsFile();
        try {
            Files.createDirectories(file.getParent());
            Map<String, String> raw = new LinkedHashMap<>();
            rules.forEach((tool, decision) -> raw.put(tool, decision.verdict().name()));
            JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), raw);
        } catch (IOException e) {
            throw new MyccException("写入权限规则失败: " + file + " / " + e.getMessage(), e);
        }
    }

    /** verdict 名 → 决策解析；仅识别 ALLOW/DENY/ASK，其余视为损坏数据报错。 */
    private static PermissionDecision toDecision(String tool, String verdictName) {
        PermissionVerdict verdict;
        try {
            verdict = PermissionVerdict.valueOf(verdictName);
        } catch (IllegalArgumentException e) {
            throw new MyccException("非法权限判定值: " + verdictName + "（工具 " + tool + "）");
        }
        return switch (verdict) {
            case ALLOW -> PermissionDecision.ALLOW;
            case ASK -> PermissionDecision.ASK;
            case DENY -> PermissionDecision.deny("权限规则拒绝: " + tool);
        };
    }
}