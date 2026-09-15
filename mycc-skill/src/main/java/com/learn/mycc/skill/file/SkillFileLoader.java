package com.learn.mycc.skill.file;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.bean.InitializingBean;
import com.learn.mycc.core.skill.SkillDefinition;
import com.learn.mycc.core.skill.SkillRegistry;
import com.learn.mycc.storage.file.FileStorage;
import com.learn.mycc.storage.file.WorkspaceStorage;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * SKILL.md 文件技能加载器：容器启动时（{@link InitializingBean#afterPropertiesSet()}）从
 * 全局（{@code ~/.mycc}）与工作区（{@code <workspace>/.mycc}）两个 {@link WorkspaceStorage}
 * 风格的 key 空间中筛选 {@code skills/<name>/SKILL.md} 并注册，不再自行拼接文件路径。
 * <p>顺序语义：先注册全局（唯一校验，与注解/全局内重名即启动期快速失败），再注册项目
 * （{@link SkillRegistry#override}，同名覆盖全局/注解）。技能名取自 key 段（目录名）。</p>
 * <p>无匹配 key 时静默返回（空加载不崩）；启动时加载一次，不热更新。</p>
 */
@Component
public class SkillFileLoader implements InitializingBean {

    /** 技能 key 前缀与文件名共同界定 {@code skills/<name>/SKILL.md}。 */
    private static final String SKILLS_PREFIX = "skills/";

    private final SkillRegistry registry;
    private final FileStorage globalStorage;
    private final WorkspaceStorage workspaceStorage;

    @Inject
    public SkillFileLoader(SkillRegistry registry, FileStorage globalStorage, WorkspaceStorage workspaceStorage) {
        this.registry = registry;
        this.globalStorage = globalStorage;
        this.workspaceStorage = workspaceStorage;
    }

    @Override
    public void afterPropertiesSet() {
        registerFrom(globalStorage.keys(), globalStorage::read, false);
        registerFrom(workspaceStorage.keys(), workspaceStorage::read, true);
    }

    /** 从某存储的 key 列表筛选技能文件、解析并注册（override=true 用覆盖注册）。 */
    private void registerFrom(List<String> keys, Function<String, Optional<String>> reader, boolean override) {
        keys.stream()
                .filter(SkillFileLoader::isSkillFile)
                .sorted()
                .forEach(key -> {
                    String content = reader.apply(key).orElse("");
                    SkillFileParser.ParsedSkill parsed = SkillFileParser.parse(skillName(key), content);
                    SkillDefinition definition = new SkillDefinition(
                            parsed.name(), parsed.description(), parsed.instructions(), parsed.trigger());
                    if (override) {
                        registry.override(definition);
                    } else {
                        registry.register(definition);
                    }
                });
    }

    /** @return key 是否为单层技能文件 {@code skills/<name>/SKILL.md}（name 不含额外斜杠）。 */
    private static boolean isSkillFile(String key) {
        String suffix = "/" + SkillFileParser.SKILL_FILE;
        if (!key.startsWith(SKILLS_PREFIX) || !key.endsWith(suffix)) {
            return false;
        }
        String name = key.substring(SKILLS_PREFIX.length(), key.length() - suffix.length());
        return !name.isEmpty() && !name.contains("/");
    }

    /** 从 key 提取技能名（{@code skills/<name>/SKILL.md} 的 name 段）。 */
    private static String skillName(String key) {
        String suffix = "/" + SkillFileParser.SKILL_FILE;
        return key.substring(SKILLS_PREFIX.length(), key.length() - suffix.length());
    }
}