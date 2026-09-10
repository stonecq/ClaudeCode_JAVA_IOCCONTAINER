# v2 开发计划（执行步骤）

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务执行。任务用 `- [ ]` 勾选跟踪。
>
> v1（M1–M6）已完成，见 `docs/plans/2026-08-27-v1-development-plan.md`。本文为 v2（M7–M13）路线图，只细化首个里程碑 M7；其余里程碑在其启动时另写详细 design/plan（沿用 `docs/superpowers/` 模式）。
>
> **2026-09-07 更新（Spring 化重构 S0–S3 已完成）**：见 `docs/superpowers/specs/2026-09-04-spring-ioc-refactor-design.md`。容器补齐 `@Configuration`+`@Bean`、`@Scope(PROTOTYPE)`、`registerSingleton`、接口可匹配、按名查找；`Main` 瘦身为「开容器 → start → 执行命令 → close」。M9–M13 新增能力一律按此约定装配：无状态实现 `@Component`，运行时对象/接口返回类型在 `@Configuration` 里以 `@Bean` 声明，注册表用 `registerSingleton`。

**Goal:** 在 v1 骨架之上补齐 7 项能力——Hook 系统 → 权限管理 → Memory 长期记忆 → Skill 技能 → Planning 规划 → Subagent 子代理 → Web 界面（Jetty + SSE），形成功能更完整、界面可插拔的简易版 Claude Code。

**Architecture:** 延续 PRD 第 4 节的单向依赖与扩展点纪律。Hook / Skill 复用 IoC 的 `BeanPostProcessor` 机制（与 `ToolRegistry` 同构）；权限审批走 `tool_call_before` 钩子；Memory 基于 `Storage` SPI；Planning / Subagent 是 `AgentLoop` 的扩展；Web 复用 `InteractionPort`，`mycc-agent` 零改动。

**Tech Stack:** JDK 17、Maven 多模块、JUnit5 + AssertJ、SLF4J + Logback、Jackson、JLine、picocli；M13 增 `org.eclipse.jetty:jetty-server`（嵌入式 + SSE）。

---

## 推进原则

- **增量、小步**：一个阶段完成并验证后再进入下一阶段，不一口气做完。
- **TDD**：先写失败测试 → 实现 → 验证通过。
- **每阶段一个提交点**：阶段完成时提交（仅在用户要求时）。
- **阶段间人工检查点**：每个阶段结束向用户汇报，确认后再继续。

## 阶段总览（依赖顺序）

| 阶段 | 模块 | 内容 | 验收标准 |
| --- | --- | --- | --- |
| M7 | mycc-core + mycc-agent | Hook 系统：激活 `@Hook`、`HookRegistry`（BeanPostProcessor）、事件模型、埋点触发 | 钩子在生命周期/工具前后按序触发，异常不崩主流程，单测全绿 |
| M8 | mycc-agent（+ storage） | 权限管理：工具调用审批流（自动允许/拒绝/询问/规则）、高风险工具默认审批、决策持久化 | bash/write_file 触发审批，决策可持久化 |
| M9 | mycc-storage + mycc-agent | Memory 长期记忆：用户/项目/会话三层记忆读写 + 注入提示词 + 关键词检索 | 记忆落盘、可检索、注入请求上下文 |
| M10 | mycc-core + 加载器 | Skill 技能：`@Skill` 注解 + `SKILL.md` 文件加载，作为可调用/可注入能力 | 示例技能可加载并注入/触发 |
| M11 | mycc-agent | Planning 规划：计划模式生成多步 Plan，逐步执行、核对、推进 | 计划生成并逐步执行 |
| M12 | mycc-agent | Subagent 子代理：主代理派生子代理（独立提示词 + 工具子集），结果汇总 | 嵌套循环端到端通过 |
| M13 | mycc-web（新模块）+ mycc-app | Web 界面：Jetty + SSE + `WebPort`（实现 InteractionPort）、REST 会话 CRUD | 浏览器流式渲染，会话管理可用，agent 零改动 |

> 依赖关系：M7 是 M8 的基础（权限审批走 `tool_call_before` 钩子）；M9/M10 相对独立；M11/M12 是较重的 agent 循环扩展；M13 复用 `InteractionPort`，在前 6 项完成后收口。

---

## M7：Hook 系统（mycc-core + mycc-agent）——详细任务

**目标**：激活 v1 已预留的 `@Hook` 注解（事件名已注释为 session_start / session_end / tool_call_before / tool_call_after / error / user_prompt_submit），新增 `HookRegistry`（与 `ToolRegistry` 同构的 `BeanPostProcessor`），并在 agent 循环埋点触发。

**复用现有机制**：
- `@Hook` 注解：`mycc-core/src/main/java/com/learn/mycc/core/annotation/Hook.java`
- `ToolRegistry` 的 BeanPostProcessor 模式：`mycc-core/src/main/java/com/learn/mycc/core/tool/ToolRegistry.java`
- `IocContainer` 门面 + 后处理器注册：`mycc-core/src/main/java/com/learn/mycc/core/context/IocContainer.java`

**Files（预计）**：
- Create: `mycc-core/.../hook/HookEventType.java`（枚举 6 事件）
- Create: `mycc-core/.../hook/HookEvent.java`（type + payload + sessionId 上下文）
- Create: `mycc-core/.../hook/HookDefinition.java`（event + method + bean）
- Create: `mycc-core/.../hook/HookRegistry.java`（BeanPostProcessor，event → 订阅者列表）
- Create: `mycc-core/.../hook/HookDispatcher.java`（同步/异步执行 + 异常容忍）
- Update: `IocContainer`（注册 HookRegistry + `getHookRegistry()`）
- Update: `mycc-agent/.../loop/AgentLoop.java`（session_start / session_end / user_prompt_submit / error 埋点）
- Update: `mycc-agent/.../tool/ToolCallExecutor.java`（tool_call_before / tool_call_after 埋点）
- Test: 各模块对应单测

**任务分解（TDD）**：
- [ ] M7-1 事件模型：`HookEventType` 枚举 + `HookEvent` record。测试：枚举值齐全、事件构造。
- [ ] M7-2 `HookRegistry`：实现 `BeanPostProcessor`，扫描 `@Hook` 方法按事件名注册（多订阅者并存）。测试：注册、按事件名取订阅者、未标注类忽略。
- [ ] M7-3 `HookDefinition` 反射调用：绑定 `HookEvent` 参数反射调用订阅方法。测试：参数绑定、方法异常。
- [ ] M7-4 `HookDispatcher`：同步执行按序、异步执行可选；单个钩子异常捕获不中断（可配置是否阻断）。测试：顺序、异常容忍、异步。
- [ ] M7-5 埋点：`AgentLoop` / `ToolCallExecutor` 在正确位置派发事件。测试：一次会话产生 session_start → tool_call_before → tool_call_after → session_end 序列。
- [ ] M7-6 装配接入：`IocContainer` 注册 `HookRegistry`、暴露 `getHookRegistry()`。测试：容器装配后能列出已注册钩子。

**M7 验收**：`mvn -pl mycc-core,mycc-agent -am test` 全绿；一次工具循环的钩子事件序列正确，钩子抛异常不影响主流程。
**M7 提交点**：`feat(core): hook system`（或按用户提交节奏拆分）。

---

## M8–M13 概览（启动时再细化）

- **M8 权限管理**：工具调用审批流（自动允许 / 自动拒绝 / 每次询问 / 按规则）；高风险工具（bash、write_file）默认审批；决策持久化（同一会话/项目记忆授权结果），基于 `Storage`；集成点走 M7 的 `tool_call_before` 钩子。
- **M9 Memory 长期记忆**：用户/项目/会话三层记忆读写、注入提示词、按关键词检索；基于 `Storage` SPI。**（主体已完成，见文末「v2 后续增强」中的 M9b 记忆清理）**
- **M10 Skill 技能**：`@Skill`（能力描述 + 提示词 + 触发条件）注解方式 + `SKILL.md`/skill 目录文件方式；加载为可调用/可注入能力。
- **M11 Planning 规划**：计划模式生成多步 `Plan`（步骤列表），逐步执行、核对、推进；预留与 Subagent 结合。
- **M12 Subagent 子代理**：主代理派生子代理，独立系统提示 + 工具子集，结果汇总回主代理；基于同一 `AgentLoop`、不同 `Session`/上下文（嵌套循环）。
- **M13 Web 界面**：新建 `mycc-web` 模块，嵌入式 Jetty + SSE，`WebPort` 实现 `InteractionPort`；REST 会话创建/列出/删除；`mycc-app` 选择绑定 Web。

---

## v2 后续增强（未排期，启动时再细化）

- **M9b 记忆清理**：记忆数量阈值检测与清理。USER/PROJECT 层索引条数超上限时触发清理，触发点候选：save 后 / **回合结束（SESSION_END）** / 下次注入时，待定。清理策略待定：LLM 主动整理（超限提醒后请 LLM 用 `delete_memory` 自清）/ 规则最旧淘汰（`FileStorage.lastModified`）/ LLM 整理 + 规则兜底（防无限膨胀）。

## v2 完成定义（DoD）

- [ ] M7–M13 全部验收通过，测试全绿。
- [ ] Web 会话页流式渲染 agent 输出（SSE），复用 `InteractionPort`，`mycc-agent` 零改动。
- [ ] 权限审批、长期记忆、技能、规划、子代理在 CLI 与 Web 两种界面下均可用。
- [ ] 开发日志按日更新；git 历史清晰（每阶段一个提交）。
