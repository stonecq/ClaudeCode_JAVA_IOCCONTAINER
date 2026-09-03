package com.learn.mycc.storage.permission;

import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.permission.PermissionDecision;
import com.learn.mycc.core.permission.PermissionVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPermissionRuleStoreTest {

    @TempDir
    Path workspace;

    JsonPermissionRuleStore store;

    @BeforeEach
    void setUp() {
        store = new JsonPermissionRuleStore(new ApplicationConfig(workspace));
    }

    /** 记住的规则后可回读：会话内存与项目文件双写。 */
    @Test
    void rememberThenResolveReadsBack() {
        store.remember("bash", PermissionDecision.ALLOW);
        assertThat(store.resolve("bash")).isEqualTo(PermissionDecision.ALLOW);
    }

    /** 无规则时返回 null，交由 PermissionPolicy 走风险默认。 */
    @Test
    void noRuleReturnsNull() {
        assertThat(store.resolve("bash")).isNull();
    }

    /** 规则文件落在 <workspace>/.mycc/permissions.json，且内容为 tool→verdict 的 JSON。 */
    @Test
    void filePersistsAtWorkspaceMyccPermissionsJson() throws Exception {
        store.remember("bash", PermissionDecision.ALLOW);
        Path file = workspace.resolve(".mycc").resolve("permissions.json");
        assertThat(Files.isRegularFile(file)).isTrue();
        String json = Files.readString(file);
        assertThat(json).contains("\"bash\"", "ALLOW");
    }

    /** clearSession 只清内存：持久文件仍生效，规则继续可解析。 */
    @Test
    void clearSessionKeepsPersistedRules() {
        store.remember("bash", PermissionDecision.ALLOW);
        store.clearSession();
        assertThat(store.resolve("bash")).isEqualTo(PermissionDecision.ALLOW);
    }

    /** 会话内存优先于项目文件：即使磁盘规则被改，本次会话记忆仍生效。 */
    @Test
    void sessionRuleTakesPrecedenceOverProject() {
        store.remember("bash", PermissionDecision.ALLOW);
        JsonPermissionRuleStore other = new JsonPermissionRuleStore(new ApplicationConfig(workspace));
        other.remember("bash", PermissionDecision.deny("磁盘覆盖"));
        assertThat(store.resolve("bash")).isEqualTo(PermissionDecision.ALLOW);
    }

    /** 新实例从持久文件加载规则：跨会话（跨进程）持久生效。 */
    @Test
    void newInstanceReadsPersistedProjectRules() {
        store.remember("read_file", PermissionDecision.ALLOW);
        JsonPermissionRuleStore fresh = new JsonPermissionRuleStore(new ApplicationConfig(workspace));
        assertThat(fresh.resolve("read_file").verdict()).isEqualTo(PermissionVerdict.ALLOW);
    }

    /** 未知 verdict 名的损坏文件按读取失败处理（fail-fast，与配置加载一致）。 */
    @Test
    void corruptedVerdictNameFailsFast() throws Exception {
        Path file = workspace.resolve(".mycc").resolve("permissions.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"bash\":\"MAYBE\"}");
        try {
            store.resolve("bash");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("非法权限判定值");
            assertThat(e.getMessage()).contains("MAYBE");
            return;
        }
        throw new AssertionError("expected failure on corrupted verdict name");
    }
}