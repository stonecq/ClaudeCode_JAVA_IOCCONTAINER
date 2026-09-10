package com.learn.mycc.skill.file;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.bean.InitializingBean;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.exception.MyccException;
import com.learn.mycc.core.skill.SkillDefinition;
import com.learn.mycc.core.skill.SkillRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * SKILL.md 文件技能加载器：容器启动时（{@link InitializingBean#afterPropertiesSet()}，
 * 依赖注入完成后）扫描全局与项目两级技能目录，解析并注册进 {@link SkillRegistry}。
 * <p>顺序语义：先注册全局（唯一校验，与注解/全局内重名即启动期快速失败），再注册项目
 * （{@link SkillRegistry#override}，同名覆盖全局/注解，否则新增）。文件技能与注解技能
 * 共享同一注册表，{@code invoke_skill} 与技能目录自动覆盖文件技能，无需额外接线。</p>
 * <p>目录缺失或没有含 SKILL.md 的子目录时静默跳过（空加载不崩）；启动时加载一次，
 * 不热更新。</p>
 */
@Component
public class SkillFileLoader implements InitializingBean {

    /** 应用配置根目录名（全局与项目下统一命名）。 */
    private static final String APP_ROOT_DIR = ".mycc";
    /** 技能目录名。 */
    private static final String SKILLS_DIR = "skills";

    private final SkillRegistry registry;
    private final ApplicationConfig config;

    @Inject
    public SkillFileLoader(SkillRegistry registry, ApplicationConfig config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public void afterPropertiesSet() {
        Path globalRoot = Path.of(System.getProperty("user.home"), APP_ROOT_DIR, SKILLS_DIR);
        Path projectRoot = config.getWorkspacePath().resolve(APP_ROOT_DIR).resolve(SKILLS_DIR);
        load(registry, globalRoot, projectRoot);
    }

    /**
     * 加载指定全局与项目技能根目录：全局用唯一注册、项目用覆盖。
     *
     * @param registry    技能注册表
     * @param globalRoot  全局技能根（可为不存在的路径）
     * @param projectRoot 项目技能根（可为不存在的路径）
     */
    static void load(SkillRegistry registry, Path globalRoot, Path projectRoot) {
        scanAndRegister(registry, globalRoot, false);
        scanAndRegister(registry, projectRoot, true);
    }

    /** 扫描根下每个含 SKILL.md 的一级子目录并注册；根缺省或非目录时静默返回。 */
    private static void scanAndRegister(SkillRegistry registry, Path root, boolean override) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve(SkillFileParser.SKILL_FILE)))
                    // 排序保证扫描顺序确定，输出稳定可预期
                    .sorted(Comparator.comparing(dir -> dir.getFileName().toString()))
                    .forEach(dir -> register(registry, dir, override));
        } catch (IOException e) {
            throw new MyccException("扫描技能目录失败: " + root, e);
        }
    }

    private static void register(SkillRegistry registry, Path skillDir, boolean override) {
        SkillFileParser.ParsedSkill parsed = SkillFileParser.parse(skillDir);
        SkillDefinition definition = new SkillDefinition(
                parsed.name(), parsed.description(), parsed.instructions(), parsed.trigger());
        if (override) {
            registry.override(definition);
        } else {
            registry.register(definition);
        }
    }
}