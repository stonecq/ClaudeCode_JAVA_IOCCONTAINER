# M6：CLI 界面 + fat-jar 启动器设计文档

- 版本：v0.1（设计待用户审阅）
- 日期：2026-09-02
- 状态：草案，待审阅
- 目标：新增 `mycc-cli` 模块（JLine + picocli + ANSI 全事件分色），以命令面替代交互式菜单；`mycc-app` 只装配并绑定 CLI；shade 打包单 fat-jar 可运行。

---

## 1. 背景与目标

### 1.1 背景

当前 M5 的交互演示由 `AgentDemoApp` + `ConsolePort` 承担：`ConsolePort` 兼作渲染与 `SessionPicker`（交互式编号菜单），`AgentDemoApp` 里内置手动交互循环。其不足：

- 交互了无命令行解析：无法 `sessions` / `resume <id>` 等命令式操作，只能启动后弹菜单。
- `ConsolePort` 用裸 `PrintStream` + `BufferedReader`，Windows 控制台 GBK 码页下中文/流式输出存在乱码风险，且无 JLine 历史/多行能力。
- 无 fat-jar，只能 `mvn exec:java` 运行。

### 1.2 目标

1. 新增 `mycc-cli` 模块：`CliPort`（`InteractionPort` 实现，JLine `Terminal` + 分色渲染）、`ReplLoop`（JLine `LineReader` 历史/多行 + 斜杠命令 + 逐轮异常容忍）、picocli 命令面。
2. 命令式交互：裸 `mycc` 新会话；`mycc resume [id]` 续聊；`mycc sessions` / `mycc tools` / `mycc config` 只读命令。
3. `mycc-app` 收敛为纯装配器：选绑定 `mycc-cli`。
4. ANSI 分色渲染全部事件；非 TTY（管道/重定向/IDE）降级为无 ANSI 纯文本。
5. shade 单 fat-jar：`java -jar mycc-app/target/mycc-app.jar` 可运行。

### 1.3 非目标

- 不做 Web UI（v2）。
- 不做会话重命名 / 删除 / 搜索（后续再加）。
- 不做补全 / 斜杠命令的模糊匹配进阶（JLine 基础历史即可）。
- 不改 agent 循环 / 存储 / 工具系统逻辑（M6 只换交互层）。

---

## 2. 已确认的交互决策（用户拍板）

| 问题 | 决策 |
| --- | --- |
| ConsolePort 去留 | **删除**，由 `CliPort`（mycc-cli）全面取代；测试迁入 mycc-cli |
| REPL 循环归属 | **mycc-cli 的 `ReplLoop`** 拥有循环；mycc-app 只装配 + 绑定 |
| 渲染策略 | **全事件分色**；非 TTY 降级无 ANSI；ANSI 开关由构造注入（测试可覆写）+ 运行时 isAnsiSupported 判定 |
| 命令面 | `mycc` `mycc resume [id]` `mycc sessions` `mycc tools` `mycc config`；**删除 `SessionPicker`**（交互式菜单模型被命令替代） |
| 裸命令语义 | 裸 `mycc` **总是新建会话**；`mycc resume`（无 id）续最近一次会话；`mycc resume <id>` 续指定会话 |

---

## 3. 分层设计

```
mycc-agent · mycc-core · mycc-ai · mycc-tools · mycc-storage
        ▲
mycc-cli(新)  →  mycc-ui(InteractionPort / OutputEvent 协议)     ← 实现方
        ▲
mycc-app(Main: 装配 → 绑定 mycc-cli → 执行命令)                  ← 启动器
```

> 依赖单向、无环：mycc-cli 依赖 core/ui/ai/tools/storage/agent + jline + picocli；mycc-app 依赖 mycc-cli 等全部模块。「新增界面 = 新增模块实现 InteractionPort + 在 mycc-app 绑定」的扩展点自此成立。

### 3.1 渲染层 `CliPort`（mycc-cli，实现 `InteractionPort`）

- 构造注入 `Terminal terminal` + `boolean ansi`（测试覆写用）+ `boolean showReasoning`。
- 渲染目标为 `terminal.writer()`（JLine 统一处理 Windows 控制台 UTF-8 码页，规避 GBK 乱码风险）。
- 事件 → 分色（TTY 时）：

| 事件 | 样式 |
| --- | --- |
| USER | 青色 `我 > {content}` |
| THINKING | 灰色 + 斜体，沿用当前思考块状态机（开启/折叠由 showReasoning 控制） |
| TOKEN | 默认正文色 |
| TOOL_CALL | 黄色 |
| TOOL_RESULT | 绿色 |
| ERROR | 红色 |
| DONE | 收尾换行等 |

- 非 ANSI（关闭 / 非 TTY）：同结构纯文本，无转义码。
- 复用 agent 侧共享封装更好，但 JLine 渲染属于 UI 实现方：ANSI 码序列封装与转义判断留在 mycc-cli。

### 3.2 循环层 `ReplLoop`（mycc-cli）

- 持有 JLine `Terminal` + `LineReader`（历史/多行），构造注入 `port` 与 `AgentLoop`。
- `run()`：循环读行 → `{port.onEvent(USER 事件) → agent.run(line)}` 包 try/catch，异常转 ERROR 事件后继续，不崩会话。
- 斜杠命令：`/exit` 结束；`/clear` 清屏（ANSI 可用时）。
- EOF（Ctrl+D）结束循环。
- 不加 JLine 提示符：回显靠 USER 事件「我 > 」前缀（延续 M5 免 prompt 决策，语义与历史回放一致）。

### 3.3 命令面 `MyccCommand`（mycc-cli，picocli）

| 命令 | 行为 |
| --- | --- |
| `mycc`（裸） | `Session.create()` → 无回放 → 构建 `AgentLoop` → `ReplLoop.run()` |
| `mycc resume [id]` | 无 id → `store.latest()`；有 id → `store.load(id)`；加载成功后 `SessionReplayer.replay(session, port)` 回放历史 → 绑同 session 建 `AgentLoop` → `ReplLoop.run()` |
| `mycc sessions` | 打印 `SessionStore.list()`（id + title + 修改时间） |
| `mycc tools` | 打印 `ToolRegistry.getAll()` 工具名与描述 |
| `mycc config` | 打印已知配置项（当前为 `showReasoning`）经 `ConfigService.get` 解析后的生效值 |

- 装配依赖（Terminal / CliPort / ReplLoop / SessionStore / ToolRegistry / LlmProvider）经构造器注入，逐层上级装配、命令只负责编排。
- `AgentLoop` 沿用 `withToolRegistry(port, provider, registry, model, 10, store, session)` 7 参重载（显式会话绑定）。

### 3.4 装配层 `Main`（mycc-app）

- 流程：装配 IoC 容器 → `ToolRegistry` → `OPENCODE_KEY` → `OpenAiCompatProvider`（复用现有 `openCodeDsProvider` 逻辑，key 仅存环境变量）→ `FileStorage` + `SessionStore` → 建 `Terminal` / `CliPort` / `ReplLoop` → `new CommandLine(new MyccCommand(...)).execute(args)`。
- shade 打包：`Main-Class` 指向 `com.learn.mycc.app.Main` + `ServicesResourceTransformer` 等（保 JLine `TerminalBuilder` ServiceLoader 在 shade 后可用）。
- **删除**：`ConsolePort`、`AgentDemoApp`（职责并入 CliPort / ReplLoop / MyccCommand）；`SessionPicker`（mycc-agent）删除。
- 父 pom 增 `<module>mycc-cli</module>`。

---

## 4. 错误处理

- `resume <id>` 不存在 → stderr/typed 报「找不到会话 <id>」→ 退出码 1。
- `resume`（无 id）但无任何历史 → 报「没有历史会话」→ 退出码 1（不自动新建）。
- REPL 每轮异常 → ERROR 事件（红色）显示 → 继续下一轮。
- 损坏 JSON / IO 异常 → 沿用现有 `MyccException` 上抛，不新做容错。
- API key 缺失 → 复用现有引导提示，不进入对话，退出码非 0。

---

## 5. 测试计划（JUnit5 + AssertJ，先写失败测试）

| 层 | 测试 |
| --- | --- |
| CliPort | 各事件分色（ANSI 开/关）；THINKING 显示/隐藏；USER 前缀「我 > 」；ANSI 关闭时纯文本、无转义码 |
| ReplLoop | `/exit` 结束；`/clear` 清屏；每轮异常被容忍并转 ERROR 事件；EOF 结束；USER 事件先于 `agent.run` 发出 |
| MyccCommand | 裸命令新建会话 + 进 REPL；`resume`（无 id）续最近；`resume <id>` 续指定；id 不存在退出 1；无历史 resume 退出 1；`sessions` / `tools` / `config` 只读输出（config 断言 `showReasoning` 生效值）；resume 触发 `SessionReplayer.replay` |
| 迁移 | `ConsolePortTest` 渲染类用例并入 CliPortTest；`selectMenu` （6 例）删除；`SessionMenuTest`、`SessionPickerTest` 一并删除 |

---

## 6. 改动文件清单

| 文件 | 动作 |
| --- | --- |
| `pom.xml` | 增 `<module>mycc-cli</module>` |
| `mycc-cli/pom.xml` | 新增（依赖 core/ui/ai/tools/storage/agent + jline + picocli） |
| `mycc-cli/.../CliPort.java` | 新增（`InteractionPort` 实现，JLine Terminal + 分色渲染 + 免 prompt） |
| `mycc-cli/.../ReplLoop.java` | 新增（JLine LineReader 历史/多行 + /exit /clear + 逐轮异常容忍） |
| `mycc-cli/.../command/MyccCommand.java` | 新增（picocli 根命令 + 子命令，构造注入装配依赖） |
| `mycc-cli/.../command/ResumeCommand.java` 等 | 新增（子命令：resume / sessions / tools / config） |
| `mycc-app/.../app/Main.java` | 新增（装配 + 绑定 mycc-cli + 执行命令；shade Main-Class） |
| `mycc-app/.../app/ConsolePort.java` | 删除（职责并入 CliPort） |
| `mycc-app/.../app/AgentDemoApp.java` | 删除（职责并入 Main + MyccCommand + ReplLoop） |
| `mycc-agent/.../session/SessionPicker.java` | 删除（菜单模型被命令替代） |
| `mycc-app/pom.xml` | 加 mycc-cli 依赖 + shade 配置 |
| 对应测试文件 | 新增 / 迁移 / 删除（见第 5 节） |

---

## 7. 验证

- `mvn clean install` 全绿（新增 mycc-cli 测试；现有 126 例不回归，删除 selectMenu 相关则相应更新）。
- 手动 `java -jar mycc-app/target/mycc-app.jar`：造两段历史 → `mycc` 新会话；`mycc resume` 续最近并回放历史后续聊；`mycc sessions` 列表正确；ANSI 分色正确；非 TTY 重定向无转义码污染。