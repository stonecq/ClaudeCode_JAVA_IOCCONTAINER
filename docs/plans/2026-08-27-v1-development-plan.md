# v1 开发计划（执行步骤）

> ✅ **v1 已完成（2026-09-02）**：M1–M6 全部验收通过，126 测试全绿，`java -jar mycc-app/target/mycc-app.jar` 可交互运行。后续工作见 `docs/plans/2026-09-03-v2-development-plan.md`。

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务执行。任务用 `- [ ]` 勾选跟踪。

**Goal:** 完成 v1 骨架——自研 IoC + `@Tool` 工具系统 + agent 循环 + 可插拔 LLM(Mock) + 存储抽象 + CLI，形成一个可运行、可验证、可扩展的简易版 Claude Code。

**Architecture:** 按 PRD 第 4 节：9 个 Maven 模块，依赖单向、可插拔。`mycc-core` 提供 IoC/注解/ToolRegistry 机制层；`mycc-ui` 只定义 UI 接口（InteractionPort/OutputEvent）；上层模块通过扩展点接入，具体 UI 渲染在各 UI 模块。分 6 个阶段（M1-M6）增量推进，每阶段可独立运行验证。

**Tech Stack:** JDK 17、Maven 多模块、JUnit5 + AssertJ、SLF4J + Logback、JLine、picocli、Jackson、Maven Shade（fat-jar）。

---

## 推进原则

- **增量、小步**：一个阶段完成并验证后再进入下一阶段，不一口气做完。
- **TDD**：先写失败测试 → 实现 → 验证通过。
- **每阶段一个提交点**：阶段完成时提交，可随时回退。
- **阶段间人工检查点**：每个阶段结束向用户汇报，确认后再继续。

## 阶段总览（依赖顺序）

| 阶段 | 模块 | 内容 | 验收标准 |
| --- | --- | --- | --- |
| M1 | mycc-core | IoC 容器 + 注解 + BeanPostProcessor + ToolRegistry 骨架 | `mvn -pl mycc-core test` 全绿；@Component/@Inject 组装、@Tool 注册 |
| M2 | mycc-core + mycc-tools | 工具系统完善（Schema 生成）+ 7 个内置工具 | 启动注册全部工具；重名报错；工具单测全绿 |
| M3 | mycc-ai | LlmProvider + MockProvider + OpenAiCompatProvider | Mock 离线跑通端到端；配置后可接真实服务 |
| M4 | mycc-agent + mycc-ui | mycc-ui 接口（InteractionPort/OutputEvent）+ AgentLoop + ToolCallExecutor + 流式 | 一次完整工具循环端到端通过 |
| M5 | mycc-storage | Storage SPI + FileStorage + ConfigService | 会话 JSON 落盘/恢复 |
| M6 | mycc-cli | CliPort（实现 mycc-ui）+ mycc-app 启动器（选择绑定 CLI） | CLI 全链路 + fat-jar 可运行 |

---

## M1：自研 IoC 容器（mycc-core）——详细任务

**目标**：注解扫描、依赖注入（构造器优先 + 字段兜底）、单例生命周期、BeanPostProcessor、IocContainer 门面、`@Tool` 捕获注册。

**Files:**
- Create: `pom.xml`（父 pom：modules、JDK17、compiler、junit5、shade 预留）
- Create: `mycc-core/pom.xml`
- Create: `mycc-core/src/main/java/com/learn/mycc/core/annotation/` → `@Component` `@Inject` `@Tool` `@ToolParam` `@Hook`（预留）
- Create: `mycc-core/src/main/java/com/learn/mycc/core/exception/MyccException.java`
- Create: `mycc-core/src/main/java/com/learn/mycc/core/bean/BeanDefinition.java`
- Create: `mycc-core/src/main/java/com/learn/mycc/core/scan/AnnotationScanner.java`
- Create: `mycc-core/src/main/java/com/learn/mycc/core/bean/BeanFactory.java`
- Create: `mycc-core/src/main/java/com/learn/mycc/core/bean/BeanPostProcessor.java`
- Create: `mycc-core/src/main/java/com/learn/mycc/core/context/IocContainer.java`（含 DefaultIocContainer）
- Create: `mycc-core/src/main/java/com/learn/mycc/core/tool/ToolDefinition.java`、`ToolRegistry.java`
- Test: `mycc-core/src/test/java/com/learn/mycc/core/` → `AnnotationScannerTest` `BeanFactoryTest` `IocContainerTest` `ToolRegistryTest` `LifecycleTest`

### Task M1-1: 父 pom + mycc-core 模块骨架
- [ ] 建父 `pom.xml`：`<modules>` 含 mycc-core；`maven.compiler.source/target=17`；`dependencyManagement` 锁定 junit5/assertj/slf4j/logback/jackson/jline/picocli；`maven-surefire-plugin`、`maven-shade-plugin`（shade 在 M6 启用）。
- [ ] 建 `mycc-core/pom.xml`：依赖 slf4j（compile）、junit5/assertj（test）。
- [ ] 运行 `mvn -pl mycc-core test`，预期：空项目构建通过（0 测试）。

### Task M1-2: 注解定义
- [ ] 定义 `@Component`（无属性）、`@Inject`（无属性）、`@Tool(name, description)`、`@ToolParam(description, required)`、`@Hook(event)`（v2 预留，仅定义）。
- [ ] 注解元数据（`ElementType`、`RetentionPolicy.RUNTIME`）。
- [ ] 写 `AnnotationScannerTest`：反射能读到 `@Tool` 注解的属性；`@Hook` 元数据存在。

### Task M1-3: BeanDefinition + AnnotationScanner
- [ ] `BeanDefinition`：`Class<?> type`、`String name`、构造器参数类型列表、注入字段集合。
- [ ] `AnnotationScanner.scan(String basePackage)`：扫描 classpath 中带 `@Component` 的类，转成 `BeanDefinition` 列表。
- [ ] `AnnotationScannerTest`：在测试包放一个 `@Component` 样例类，断言扫描到它且 BeanDefinition 字段正确。

### Task M1-4: BeanFactory 依赖注入
- [ ] 实现：按类型解析依赖；**构造器注入优先**，无可用构造器时**字段注入兜底**；缺失依赖抛 `MyccException`。
- [ ] 循环依赖检测：构造/注入过程中检测到环即启动期报错。
- [ ] `BeanFactoryTest`：构造器注入成功；字段注入成功；缺依赖报错；A↔B 循环报错。

### Task M1-5: BeanPostProcessor + IocContainer 生命周期
- [ ] `BeanPostProcessor.postProcessAfterInitialization(bean, name)` 扩展点。
- [ ] 生命周期：bean 实现 `InitializingBean`（或 `@PostConstruct` 兼容）调用 `init`；容器 `close()` 逆序销毁。
- [ ] `IocContainer`：`register(BeanDefinition...)`、`start()`、`getBean(Class)`、`getBeansOfType(Class)`、`getToolRegistry()`、`close()`。
- [ ] 测试：后处理器按注册顺序被调用；`init`/`close` 回调顺序正确；`getBeansOfType` 按类型取到全部实现。

### Task M1-6: ToolRegistry + @Tool 捕获
- [ ] `ToolDefinition`：`name`、`description`、`Method`、`bean`、`parameterSchema`（Schema 完整生成放 M2，M1 先存 Method+参数元数据）。
- [ ] `ToolRegistry` 实现 `BeanPostProcessor`：bean 创建后反射扫描 `@Tool` 方法注册；重名抛 `MyccException`；提供 `getAll()`、`get(name)`。
- [ ] `ToolRegistryTest`：`@Tool` 方法自动注册；重名报错；`getAll` 数量正确。

**M1 验收**：`mvn -pl mycc-core test` 全绿；`mycc-app` 的临时演示 main（或 `IocContainerTest` 里的装配用例）能组装一个 `@Component` 依赖图并列出已注册 `@Tool`。
**M1 提交点**：`chore: scaffold maven modules` + `feat(core): custom IoC container`。

---

## M2：工具系统 + 内置工具（mycc-core + mycc-tools）——任务清单

**目标**：`ParameterSchemaGenerator` 生成 JSON Schema；7 个内置工具全部用 `@Tool` 编写；`mycc-app` 启动能注册全部工具。

**Files:**
- Create: `mycc-core/.../tool/ParameterSchemaGenerator.java`、`ToolCall.java`、`ToolResult.java`
- Create: `mycc-tools/pom.xml` + `mycc-tools/.../tools/` 下 `FileTools`、`BashTool`、`SearchTools`（内含 read/write/edit/bash/glob/grep/search_files）
- Create: `mycc-tools/.../` 各工具单测
- Create: `mycc-app/pom.xml` + 临时 `MyccApplication`（仅装配 + 打印工具列表，M6 完善）

**任务分解：**
- [ ] M2-1 Schema 生成器：反射方法参数 → JSON Schema（`type`、`description`、`required`、枚举）。测试：基本类型/枚举/`@ToolParam` 元数据。
- [ ] M2-2 `FileTools`：`read_file` / `write_file` / `edit_file`。测试：读写正常、路径穿越拒绝、编辑替换正确。
- [ ] M2-3 `BashTool`：`bash` 执行命令，返回 stdout/stderr/exitCode；参数校验（禁空、禁注入字符）。测试：正常执行、非零退出、非法参数。
- [ ] M2-4 `SearchTools`：`glob` / `grep` / `search_files`。测试：模式匹配、大小写、无结果、目录遍历限制。
- [ ] M2-5 临时 `MyccApplication`：建容器 → 注册 mycc-tools → 打印全部工具名与 Schema。
- [ ] M2-6 `mvn clean install` 全绿；启动打印 7 个工具。

**M2 验收**：`mvn -pl mycc-core,mycc-tools,mycc-app -am test` 全绿；演示程序列出 7 个工具且 Schema 合法。
**M2 提交点**：`feat(tools): built-in toolset`。

---

## M3：LLM 抽象（mycc-ai）——任务清单

**目标**：`LlmProvider` 接口 + 流式回调；`MockProvider` 离线跑通；`OpenAiCompatProvider` 可接真实服务。

**Files:**
- Create: `mycc-ai/pom.xml`
- Create: `mycc-ai/.../spi/LlmProvider.java`、`ChatRequest`、`ChatResponse`、`ChatMessage`、`StreamChunk`、`ModelConfig`、`LlmOptions`
- Create: `mycc-ai/.../provider/MockProvider.java`、`OpenAiCompatProvider.java`
- Create: 各 Provider 单测

**任务分解：**
- [ ] M3-1 消息/请求/响应模型（record）。
- [ ] M3-2 `LlmProvider` 接口：`chat(ChatRequest, StreamSink)`（流式）。测试：接口契约。
- [ ] M3-3 `MockProvider`：关键字匹配预设回复 + 可配置模拟工具调用场景（自由发挥）。测试：命中关键字返回对应回复；模拟工具调用返回 `ChatResponse.toolCalls`。
- [ ] M3-4 `OpenAiCompatProvider`：`HttpClient` 调 `/chat/completions`（stream），映射 tool_calls。测试：Mock WebServer 返回正常/流式/错误。

**M3 验收**：`MockProvider` 离线端到端（喂一段消息 → 得到回复/工具调用）；真实 key 配置后可调通（人工验证，可选）。
**M3 提交点**：`feat(ai): pluggable LLM providers`。

---

## M4：Agent 循环（mycc-agent）——任务清单

**目标**：`AgentLoop` 多轮工具调用 + `ToolCallExecutor` + `OutputEvent` 流式下发。

**Files:**
- Create: `mycc-agent/pom.xml`
- Create: `mycc-agent/.../session/Session.java`、`Conversation.java`、`Message.java`
- Create: `mycc-agent/.../loop/AgentLoop.java`
- Create: `mycc-agent/.../tool/ToolCallExecutor.java`
- Create: `mycc-ui/pom.xml` + `mycc-ui/.../ui/InteractionPort.java`、`mycc-ui/.../ui/OutputEvent.java`（UI 抽象模块，仅接口，agent 与各 UI 共用）
- Create: `mycc-agent/.../` 端到端单测（基于 MockProvider）

**任务分解：**
- [ ] M4-1 消息/会话模型。
- [ ] M4-2 `ToolCallExecutor`：查 `ToolRegistry` → 参数绑定（JSON → 方法参数）→ 反射调用 → `ToolResult`。测试：参数转换、未知工具、异常。
- [ ] M4-3 `AgentLoop`：循环调 LLM，工具调用则执行并回填，无工具调用则结束；`maxIterations` 终止；错误回填给 LLM。
- [ ] M4-4 流式：每轮通过 `InteractionPort`（mycc-ui 定义）下发 token/tool_call/tool_result/done 事件。测试：事件序列正确。
- [ ] M4-5 端到端：MockProvider 触发"写文件→读文件"多轮，断言工具结果回填与最终回复。

**M4 验收**：一次"read→write→read"多轮工具循环端到端通过，事件流完整。
**M4 提交点**：`feat(agent): agent loop with tool calls`。

---

## M5：存储抽象（mycc-storage）——任务清单

**目标**：`Storage` SPI + `FileStorage` + `ConfigService`；会话可落盘/恢复。

**Files:**
- Create: `mycc-storage/pom.xml`
- Create: `mycc-storage/.../spi/Storage.java`
- Create: `mycc-storage/.../file/FileStorage.java`
- Create: `mycc-storage/.../config/ConfigService.java`
- Create: 单测（用 JUnit `@TempDir`）

**任务分解：**
- [ ] M5-1 `Storage` SPI + `FileStorage`（JSON，目录 `~/.mycc/`）。测试：读写/列/删、目录结构、非法路径。
- [ ] M5-2 `ConfigService`：加载配置 + 默认值 + 覆盖优先级。测试：默认值、系统属性覆盖。
- [ ] M5-3 会话序列化：`Conversation`（含工具调用与结果）↔ JSON。测试：序列化往返一致。

**M5 验收**：会话 JSON 落盘后可原样恢复。
**M5 提交点**：`feat(storage): storage SPI + file storage`。

---

## M6：CLI + 启动器（mycc-cli + mycc-app）——任务清单

**目标**：`CliPort`（实现 mycc-ui 的 `InteractionPort`，JLine + picocli）+ `mycc-app` 启动器（选择绑定 CLI）+ fat-jar 打包。

**Files:**
- Create: `mycc-cli/pom.xml` + `CliPort.java`（实现 mycc-ui 的 `InteractionPort`）、`ReplLoop.java`、picocli 命令
- Update: `mycc-app/MyccApplication.java`（完整启动 + `main` + 选择绑定 CLI 模块）
- Update: 父 pom 启用 `maven-shade-plugin`
- Create: CLI 单测

**任务分解：**
- [ ] M6-1 确认 `InteractionPort` 接口与 `OutputEvent`（已在 M4 于 mycc-ui 定义）：渲染事件、采集输入、确认。
- [ ] M6-2 `CliPort`：输出事件 → ANSI 渲染（正文/工具/结果分色）。
- [ ] M6-3 `ReplLoop`：JLine 输入（历史/补全）、斜杠命令 `/exit` `/clear`、异常不退出。
- [ ] M6-4 `mycc-app`：装配全部模块 → **选择并绑定 CLI 模块**（`CliPort` 作为 `InteractionPort` 注入 agent）→ 启动。
- [ ] M6-5 fat-jar：`mvn package` → `java -jar mycc-app/target/mycc-app.jar` 进入 REPL。

**M6 验收**：CLI 输入自然语言 → Mock LLM 驱动 → 调用内置工具 → 流式输出 → 会话落盘，全链路跑通；fat-jar 可运行。
**M6 提交点**：`feat(cli): CLI port + fat-jar launcher`。

---

## v1 完成定义（DoD）✅ 已达成（2026-09-02）

- [x] M1-M6 全部验收通过，测试全绿。
- [x] `java -jar mycc-app.jar` 可交互运行完整会话。
- [x] 新增一个界面 = 新增一个模块实现 `InteractionPort` 并在 `mycc-app` 绑定（已验证扩展点成立）。
- [x] 开发日志按日更新；git 历史清晰（每阶段一个提交）。
