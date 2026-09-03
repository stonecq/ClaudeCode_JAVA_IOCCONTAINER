# M8：权限管理（工具调用审批流）设计文档

- 版本：v0.1（已实现，随 M8 验收）
- 日期：2026-09-04
- 状态：已实现
- 目标：工具调用审批流（自动允许 / 自动拒绝 / 每次询问 / 按规则持久化）；高风险工具默认审批；决策持久化（会话内存 + 项目文件）；复用 M7 的 `tool_call_before` 钩子与 `HookDecision` 否决链路，零 agent 循环改动。

---

## 1. 背景与目标

### 1.1 背景

M7 引入了 hook 机制：`@Hook` 注解声明事件订阅、`HookDispatcher.dispatch` 返回 `HookDecision`、任一 deny 短路，被拒调用由 `AgentLoop.handleToolCalls` 以 `被钩子拦截: <reason>` 回填给 LLM（不执行工具、不派发 TOOL_CALL_AFTER）。M8 的权限审批天然挂在 `tool_call_before` 事件上，无需改动 agent 主循环。

当前工具的越界防护已在 M7 收口到 `WorkspacePaths` 校验钩子，但「是否允许 agent 执行某工具」尚无人工决策环节：`bash` / `write_file` / `edit_file` 等高影响操作会被直接执行，缺少知情与授权。

### 1.2 目标

1. 工具风险声明：`@Tool` 新增 `risk()` 属性（默认 LOW）；`bash` / `write_file` / `edit_file` 标注 HIGH。
2. 审批决策链：风险 + 规则 → 自动允许 / 自动拒绝 / 每次询问；询问交互 `[y/N/a]`（允许本次 / 拒绝 / 始终允许）。
3. 决策持久化：会话内存缓存 + 工作区 `.mycc/permissions.json` 跨会话持久；`SESSION_START` 清空内存缓存。
4. 未知工具、headless 环境一律 fail-closed 拒绝（宁可多问一层，不默认放行）。

### 1.3 非目标（YAGNI）

- 不引入 MEDIUM 等第三级风险（2 级够用）。
- `N` 拒绝不持久化为「始终拒绝」规则（仅 `a` 持久化放行）。
- 不按参数粒度配规则（本版规则按工具名；args 仅用于审批对话框展示）。
- 不做规则文件热加载（SESSION_START 仅清内存，不重读磁盘）。
- 无 Web 审批界面、无异步审批（FR-8 异步留待 YAGNI；M13 Web 复用 `UserConfirmation` 接口挂接）。

---

## 2. 已确认的交互决策（用户拍板）

| 问题 | 决策 |
| --- | --- |
| 风险声明方式 | `@Tool` 注解新增 `risk()`（默认 LOW）；高风险工具显式标注，规则文件仍可按工具覆盖 |
| 询问交互 | `[y/N/a]` —— `y`=允许本次、`N`=拒绝、`a`=始终允许（持久化） |
| 决策持久化 | 工作区 `.mycc/permissions.json` 跨会话 + 会话内存 `HashMap`（`SESSION_START` 清空） |
| 决策优先级 | 会话内存策略 > 项目文件策略 > 风险默认（HIGH→询问 / LOW→放行） |
| 未知工具 | 未注册工具按 HIGH 兜底，过审批再放行，不默认放行 |
| headless | `confirm` 为 null / 读取失败 → fail-closed 拒绝 |
| 增量集成 | 走 M7 `tool_call_before` 钩子 + `HookDecision` 否决；**零新增模块依赖、零 AgentLoop/CliContext 改动** |

---

## 3. 分层设计

```
ToolCall(bash, args)
  → tool_call_before 派发（HookDispatcher）
    → PermissionHook.authorize(HookEvent)
        1. ToolRegistry.get(name) → ToolDefinition.risk（未知工具按 HIGH 兜底）
        2. PermissionPolicy.decide(store, toolName, risk):
            规则命中（会话 > 项目）→ 返回规则决策
            无规则: risk==HIGH → ASK；risk==LOW → ALLOW
        3. ASK 时经 UserConfirmation 征询 [y/N/a]:
            y → ALLOW_ONCE（放行本次，不持久化）
            N → DENY（拒绝，不持久化）
            a → ALLOW_ALWAYS（放行 + store.remember() 写会话内存 + .mycc/permissions.json）
            headless → 默认拒绝（fail-closed）
      → HookDecision（放行 / deny(原因)）回流 agent 循环
SESSION_START → PermissionHook.clearSession → store.clearSession()
```

### 3.1 各模块职责

**mycc-core（纯机制层，无 IO/UI）**

| 组件 | 职责 |
| --- | --- |
| `annotation/ToolRisk` | 枚举 `LOW` / `HIGH` |
| `annotation/Tool` | 新增 `ToolRisk risk() default ToolRisk.LOW` |
| `tool/ToolDefinition` | 新增 `risk` 字段 + `getRisk()`；保留 4 参构造（委托 5 参，risk=LOW）使既有调用点免改 |
| `permission/PermissionVerdict` | 枚举 `ALLOW` / `DENY` / `ASK` |
| `permission/PermissionDecision` | record `(verdict, reason)`，常量 `ALLOW`/`ASK`，静态工厂 `deny(reason)` |
| `permission/PermissionRuleStore` | 接口：`resolve(String)`（无规则返 null）、`remember(String, PermissionDecision)`、`clearSession()` |
| `permission/PermissionPolicy` | 纯决策：`decide(RuleStore, toolName, risk)` —— 规则命中返规则，否则 HIGH→ASK / LOW→ALLOW；**不引用 `ToolCall`** |
| `permission/UserConfirmation` | 接口 + `ConfirmChoice`（`ALLOW_ONCE`/`DENY`/`ALLOW_ALWAYS`/`UNAVAILABLE`）；`prompt(toolName, description, args)` |

**mycc-storage（JSON 规则持久化）**

`JsonPermissionRuleStore`（`@Component`）：`sessionRules` 内存 Map + 持久 JSON（`<workspace>/.mycc/permissions.json`，tool→verdict）；`remember` 写双份，`resolve` 先会话后项目，`clearSession` 只清内存；含 no-arg + `ApplicationConfig` 双构造。

**mycc-tools（声明风险）**

`BashTool.bash`、`FileTools.write_file`/`edit_file` 标 `ToolRisk.HIGH`；只读工具（`read_file`、SearchTools 三个）保持默认 LOW。

**mycc-cli（审批交互 UI）**

`CliPermissionPrompt`（实现 `UserConfirmation`）：构造 `(ReplLoop.LineInput, PrintWriter)`；输出工具名 + 描述 + 参数 + `[y]允许本次 [N]拒绝 [a]始终允许`；循环读到合法输入；`y/a`→对应 ALLOW_ONCE/ALLOW_ALWAYS，`N/n`→DENY，空/EOF/IO 异常→UNAVAILABLE（headless 降级拒绝）。

**mycc-hooks（审批钩子）**

`PermissionHook`：手工构造（非 `@Component`，避免容器无法注入 CLI 专属 `UserConfirmation`）；构造 `(ToolRegistry, PermissionRuleStore, PermissionPolicy, UserConfirmation)`（confirm 可为 null）；`@Hook(TOOL_CALL_BEFORE)` 执行数据流产出 `HookDecision`；`@Hook(SESSION_START)` 调 `store.clearSession()`。

**mycc-app（装配接线）**

`Main` 插桩：`JsonPermissionRuleStore`（容器装配）→ `new PermissionPolicy()` → `new CliPermissionPrompt(ReplLoop.fromLineReader(reader), out)` → `new PermissionHook(registry, store, policy, confirm)` → `hookRegistry.postProcessAfterInitialization(permissionHook, "permissionHook")`。CliContext / dispatcher 链路不变。

---

## 4. 错误处理

- 未知工具：`ToolRegistry.get` 抛 `MyccException` → `PermissionHook.find` 捕获返回 null → 按 HIGH 兜底过审批。
- headless（confirm 为 null）：`deny("审批环境不可用，拒绝高风险调用: <工具>")`。
- 审批输入非法字符：循环重询，提示 `无效输入，请选择 [y/N/a]`；EOF / IO 异常 → UNAVAILABLE → 拒绝。
- 规则文件损坏：`JsonPermissionRuleStore` 读盘解析失败抛 `MyccException`（fail-fast）；空文件视为无规则。

## 5. 测试计划（JUnit5 + AssertJ，先写失败测试）

| 层 | 测试 |
| --- | --- |
| core | `ToolRiskTest`（注解默认 LOW / 显式 HIGH）；`ToolDefinitionTest`（4 参构造 risk=LOW，5 参透传）；`PermissionDecisionTest`；`PermissionPolicyTest`（无规则 LOW→ALLOW、无规则 HIGH→ASK、规则命中）· ToolRegistry 传播 risk |
| storage | `JsonPermissionRuleStoreTest`（remember→resolve 回读、clearSession 清内存不清持久、文件落 `<workspace>/.mycc/permissions.json`、无规则返 null、损坏文件报错）· `@TempDir` 指向工作区 |
| tools | `ToolRiskDeclarationTest`：bash/write_file/edit_file 的 `getRisk()==HIGH`，其余 LOW |
| cli | `CliPermissionPromptTest`（假 `LineInput`）：y→ALLOW_ONCE、N→DENY、a→ALLOW_ALWAYS、空/EOF/IO→UNAVAILABLE、非法字符→重询 |
| hooks | `PermissionHookTest`（内存态 RuleStore + 假 UserConfirmation）：低风险不问询、ALLOW_ONCE/DENY/ALLOW_ALWAYS/UNAVAILABLE、allowRule 跳过询问、denyRule 覆盖低风险、headless 拒绝、未知工具、SESSION_START 清空 |
| agent (e2e) | `PermissionFlowTest`（真实 PermissionHook + JsonPermissionRuleStore + BashTool）：ALLOW_ONCE→执行不持久化；DENY→未执行回填 `被钩子拦截: 用户拒绝调用: bash`；ALLOW_ALWAYS→执行 + 落盘 `"bash":"ALLOW"`；headless→`被钩子拦截: 审批环境不可用，拒绝高风险调用: bash` |

## 6. 改动文件清单

| 模块 | 文件 | 动作 |
| --- | --- | --- |
| core | `annotation/ToolRisk.java` | 新增 |
| core | `annotation/Tool.java` | 改：增 `risk()` |
| core | `tool/ToolDefinition.java` | 改：增 `risk` 字段 + 5 参构造 |
| core | `tool/ToolRegistry.java` | 改：注册透传 `tool.risk()` |
| core | `permission/{PermissionVerdict,PermissionDecision,PermissionRuleStore,PermissionPolicy,UserConfirmation}.java` | 新增 |
| storage | `permission/JsonPermissionRuleStore.java` | 新增 |
| tools | `BashTool.java`、`FileTools.java` | 改：标 `ToolRisk.HIGH` |
| cli | `repl/CliPermissionPrompt.java` | 新增 |
| hooks | `PermissionHook.java` | 新增 |
| app | `Main.java` | 改：装配审批钩子 |
| 测试 | 上述各模块同名 `*Test` + `PermissionFlowTest`（mycc-agent） | 新增 |

## 7. 验证

- `mvn clean install` 全量验收全绿（M8 在 158 用例基础上新增 core+storage+cli+hooks+agent 权限用例）。
- 手动 `java -jar mycc-app/target/mycc-app.jar`：触发 `bash` 调用 → `[y/N/a]` 审批 → `y` 放行、`N` 拦截回填；选 `a` 后检查工作区 `.mycc/permissions.json` 生成且含 `"bash":"ALLOW"`；新开会话验证内存缓存清空、持久规则仍生效。
- 无 key 只读命令（`--help`/`sessions`/`tools`/`config`）不受审批影响。