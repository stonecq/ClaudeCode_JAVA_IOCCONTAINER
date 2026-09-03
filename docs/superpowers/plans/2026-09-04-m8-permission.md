# M8：权限管理（工具调用审批流）实现计划

- 版本：v0.1（已完成，随 M8 验收）
- 日期：2026-09-04
- 状态：已完成
- 背景：v2 路线图（`docs/plans/2026-09-03-v2-development-plan.md`）M8 = 权限管理。PRD FR-9：工具调用审批流（自动允许 / 自动拒绝 / 每次询问 / 按规则）、高风险工具默认审批、决策持久化（同一会话/项目记忆授权结果）、结合 Storage。
- 前置（M7 复用资产）：`HookRegistry`（BeanPostProcessor，按事件分组）、`HookDispatcher.dispatch` 返回 `HookDecision`（任一 deny 短路，被拒调用由 `AgentLoop.handleToolCalls` 以 `被钩子拦截: <reason>` 回填，不执行工具、不派发 TOOL_CALL_AFTER）。

## 实现约束

- 权限审批走 `tool_call_before` 钩子 + `HookDecision` 否决链路，**零新增模块依赖、零 AgentLoop/CliContext 改动**。
- `mycc-core` 纯机制层：`PermissionPolicy` 不引用 `ToolCall`（mycc-ai），按工具名字符串决策；`UserConfirmation` 为 core 纯接口，UI 实现在 mycc-cli。
- TDD：每个任务先写失败测试再实现，阶段验收 = 该阶段测试全绿。

## TDD 任务分解（M8-1 → M8-8）

### M8-1 风险属性（mycc-core）

- 新 `core/annotation/ToolRisk.java`：枚举 `LOW` / `HIGH`。
- 改 `core/annotation/Tool.java`：新增 `ToolRisk risk() default ToolRisk.LOW;`。
- 改 `core/tool/ToolDefinition.java`：新增 `risk` 字段 + `getRisk()`；**保留 4 参构造**（委托 5 参，risk=LOW），新增 5 参构造 —— 使既有调用点（ToolRegistry:37、MyccCommandTest:156、ToolRegistryTest:59）无需改动。
- 改 `core/tool/ToolRegistry.java:37`：`register(new ToolDefinition(tool.name(), tool.description(), bean, method, tool.risk()))`。
- 测试：`ToolRiskTest`（注解默认 LOW / 显式 HIGH）、`ToolDefinitionTest`（4 参构造 risk=LOW，5 参透传）、`ToolRegistryTest` 增断言 risk 传播。✅ 全绿

### M8-2 权限模型（mycc-core）

- 新 `core/permission/PermissionVerdict.java`：枚举 `ALLOW` / `DENY` / `ASK`。
- 新 `core/permission/PermissionDecision.java`：record `(PermissionVerdict verdict, String reason)`，常量 `ALLOW` / `ASK`，静态工厂 `deny(reason)`。
- 新 `core/permission/PermissionRuleStore.java`：接口 `resolve(String)`（无规则返 null）`remember(String, PermissionDecision)` `clearSession()`。
- 新 `core/permission/PermissionPolicy.java`：纯决策 `decide(PermissionRuleStore, String, ToolRisk)` —— 规则命中返规则，否则 HIGH→ASK / LOW→ALLOW。
- 新 `core/permission/UserConfirmation.java`：接口 + `ConfirmChoice` 枚举（`ALLOW_ONCE`/`DENY`/`ALLOW_ALWAYS`/`UNAVAILABLE`）。
- 测试：`PermissionDecisionTest`；`PermissionPolicyTest`。✅ 全绿

### M8-3 规则存储（mycc-storage）

- 新 `storage/permission/JsonPermissionRuleStore.java`：`@Component`，`@Inject ApplicationConfig`；内存 `sessionRules` Map + 持久 JSON（`<workspace>/.mycc/permissions.json`，Jackson 序列化 tool→verdict）；`remember` 写双份，`resolve` 先会话后项目，`clearSession` 只清内存；含 no-arg 与 `ApplicationConfig` 双构造。
- 测试：`JsonPermissionRuleStoreTest`（@TempDir 指向工作区）——remember→resolve 回读、clearSession 清内存不清持久、文件落点、无规则返 null、损坏文件报错。✅ 全绿

### M8-4 工具风险声明（mycc-tools）

- 改 `BashTool.java:33`、`FileTools.java`（`write_file`/`edit_file`）标 `ToolRisk.HIGH`；只读工具保持 LOW。
- 测试：`ToolRiskDeclarationTest` —— bash/write_file/edit_file `getRisk()==HIGH`，其余 LOW。✅ 全绿

### M8-5 CLI 审批（mycc-cli）

- 新 `cli/repl/CliPermissionPrompt.java`：实现 `UserConfirmation`，构造 `(ReplLoop.LineInput, PrintWriter)`；输出「工具 X 需审批（高风险）+ 描述/参数 + [y]允许本次 [N]拒绝 [a]始终允许」，循环读合法输入；y/a→ALLOW_ONCE/ALLOW_ALWAYS、N/n→DENY、空/EOF/IO 异常→UNAVAILABLE、非法→重询。
- 测试：`CliPermissionPromptTest`（假 `LineInput`）7 用例。✅ 全绿（修一次包名 import：测试在 `com.learn.mycc.cli`，实现在 `com.learn.mycc.cli.repl`）

### M8-6 审批钩子（mycc-hooks）

- 新 `hooks/PermissionHook.java`（手工构造，非 `@Component`）：构造 `(ToolRegistry, PermissionRuleStore, PermissionPolicy, UserConfirmation)`（confirm 可为 null）。
  - `@Hook(TOOL_CALL_BEFORE)`：payload 非 ToolCall → ALLOW；查 `ToolDefinition` risk（未知按 HIGH）→ `policy.decide`；ASK 时经 confirm 征询：y→ALLOW、a→remember+ALLOW、N/UNAVAILABLE→deny、headless→deny fail-closed。
  - `@Hook(SESSION_START)`：`store.clearSession()` 恒 ALLOW。
- 测试：`PermissionHookTest`（内存态 RuleStore + 固定 `UserConfirmation`）11 用例。✅ 全绿

### M8-7 装配（mycc-app）

- 改 `Main.java`（`new HookDispatcher` 之前插入）：`JsonPermissionRuleStore ruleStore = container.getBean(...)` → `new PermissionPolicy()` → `new CliPermissionPrompt(ReplLoop.fromLineReader(reader), out)` → `new PermissionHook(registry, ruleStore, policy, confirm)` → `container.getHookRegistry().postProcessAfterInitialization(permissionHook, "permissionHook")`。
- `mvn -pl mycc-app -am test` 通过。✅

### M8-8 端到端 + 文档（本任务）

- `mycc-agent/pom.xml` 增 mycc-hooks test 依赖、mycc-tools 已有 test 依赖。
- 新 `agent/PermissionFlowTest.java`（`com.learn.mycc.agent`）：真实 PermissionHook + JsonPermissionRuleStore（`@TempDir` 工作区）+ BashTool；MockProvider 首轮抛 `bash("echo hi")` 高险调用，TOOL 结果后给最终回复。4 用例：
  1. `allowOnceExecutesCommandWithoutPersisting`：ALLOW_ONCE → 执行（TOOL 消息含 `exitCode=0`）不持久化；
  2. `denyBlocksExecutionAndFeedsBack`：DENY → 未执行，TOOL 消息 = `被钩子拦截: 用户拒绝调用: bash`；
  3. `allowAlwaysExecutesAndPersistsRule`：ALLOW_ALWAYS → 执行，`store.resolve("bash").allowed()` 且落盘 `.mycc/permissions.json` 含 `"bash" : "ALLOW"`；
  4. `headlessDeniesHighRiskCall`：confirm=null → TOOL 消息 = `被钩子拦截: 审批环境不可用，拒绝高风险调用: bash`。
- 文档：写 `docs/superpowers/specs/2026-09-04-m8-permission-design.md`（设计）+ 本计划文档。
- 全量验收 `mvn clean install` 全绿；人工 `java -jar mycc-app/target/mycc-app.jar` 验证审批流；更新 `dev-log/2026-09-04.md`。

## 决策要点（供复读）

- 决策优先级：会话内存策略 > 项目文件策略 > 风险默认（HIGH→询问 / LOW→放行）。
- 未知工具按 HIGH 兜底、过审批再放行；headless 一律 fail-closed 拒绝。
- `[a]` 持久化放行写入会话内存 + `.mycc/permissions.json`；`SESSION_START`（新建空会话）清内存缓存，避免恢复的旧会话继承过期内存授权。

## 验证结果

- `mvn clean install`：BUILD SUCCESS，全量用例全绿（core 53 + tools 20 + ai 10 + storage 28 + hooks 18 + agent 43 + cli 31 + app 1 等）。
- 人工验证（需 `OPENCODE_KEY`）：`bash(cmd="echo hi")` 触发 `[y/N/a]`，`y` 放行执行、`N` 拦截回填、`a` 落盘 `.mycc/permissions.json` 含 `"bash":"ALLOW"`。
- 未提交 git（按 CLAUDE.md #7，用户确认后再提交）。