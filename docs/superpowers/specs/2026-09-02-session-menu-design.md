# 会话历史菜单设计文档（Session Menu）

- 版本：v0.1（设计待用户审阅）
- 日期：2026-09-02
- 状态：草案，待审阅
- 目标「进来选对话，选完打印历史」：进入应用时若存在历史会话，展示编号菜单（含「新的对话」选项），选择后在界面打印该会话的历史，继续在其中对话。

---

## 1. 背景与目标

### 1.1 背景

当前 M5 的 `AgentDemoApp` 行为：启动时若有 `session/latest` 指针则**自动恢复最近会话**，无任何提示直接进入聊天循环。用户无法主动选择继续哪段历史，也没有查看历史的手段。

### 1.2 目标

进入应用时：
1. 遍历当前已有历史会话，显示编号菜单：「1. 《最后一次用户消息》　2. 《…》　…　N. 新的对话」。
2. 用户选择某项后，界面**打印所选会话的历史**。
3. 之后在该会话上下文里继续对话（选择新对话则开启全新会话）。

### 1.3 非目标

- 不做会话重命名 / 删除 / 搜索（后续再加）。
- 不做多行标题 / 富文本 / 分页滚动（终端单屏展示即可）。
- reasoning 思考内容不进入历史打印（会话落盘本就不含 reasoning）。

---

## 2. 已确认的交互决策（用户拍板）

| 问题 | 决策 |
| --- | --- |
| 选择已有会话后去哪聊 | **继续在该对话里聊**（合并同一上下文，不新建） |
| 无历史时是否显示菜单 | **仅有历史时才显示**；无历史直接进入新会话聊天 |
| 对话名称 | 用**该会话最后一条用户消息**，超长截断约 20 字加 `…` |
| 打印历史时是否区分角色 | **角色标记**：用户 / 工具调用 / 工具结果 / 助手 分别加前缀 |

---

## 3. 分层设计

按现有架构纪律（单向依赖、接口与实现分离），改动落在三层：

```
mycc-storage(mycc-agent 依赖的数据源)   ← lastModified() + SessionStore.list()
        └─ mycc-agent(session/聊天机制) ← AgentLoop 显式会话绑定（去自动恢复）
              └─ mycc-app(UI 编排)      ← SessionPicker/SessionReplayer + AgentDemoApp 接入
```

### 3.1 数据层（mycc-storage / mycc-agent）

- **`Storage` 接口新增**：`Optional<Long> lastModified(String key)`——返回 key 对应内容最后修改的时间戳（epoch 毫秒），内容不存在返回空。
  - `FileStorage` 实现：`Files.getLastModifiedTime(resolve(key)).toMillis()`，非 regular file 返回空；IO 异常抛 `MyccException`。
  - 唯一实现为 `FileStorage`（已确认），无测试 Mock 需同步。
- **`SessionStore` 新增**：
  - `record SessionSummary(String id, String title)`——`title` 为会话最后一条 USER 消息。
  - `List<SessionSummary> list()`——从 `storage.keys()` 过滤出 `session/<id>.json`（`session/latest` 指针自动被排除），按 `lastModified` **倒序**排列，每项 title 取该会话最后一条 `role == USER` 的 `content`；若该会话无 USER 消息则 title 用 `（空对话）` 占位。

### 3.2 UI 层（历史回放与实时渲染走同一事件协议）

为让「历史里看到的样子」与「当时实时看到的样子」一致、且未来换 UI / 上 Web 时不重复实现，历史回放与实时渲染共用 `OutputEvent` 协议：

- **`OutputEventType` 新增 `USER` 事件**：实时对话的用户提问与历史回放的用户消息统一为 `USER` 事件，`ConsolePort` 渲染 `我 > {content}`。
- **mycc-agent 新增 `SessionPicker` 接口**：`Session selectMenu(SessionStore store)`。依赖倒置——接口定义在选择方（agent 侧），实现落在渲染方（UI 侧），使上层应用只面向接口、不关心具体 UI 实现。
- **mycc-agent 新增 `SessionReplayer`**：把已持久化的历史 `Message` 序列回放为与实时一致的事件流（`USER` / `TOOL_CALL` / `TOOL_RESULT` / `TOKEN` / `DONE`）；工具调用文本用共享的 `ToolCallFormatter` 格式化（与 `AgentLoop` 实时一致），避免重复实现。
- **`ConsolePort` 实现双端口 `InteractionPort` + `SessionPicker`**：吸收菜单选择（`selectMenu`：编号列表 + 约 20 字 codepoint 安全截断 + 「新的对话」末位项 + 非法输入重读 + Ctrl+D 兜底）；`ConsolePort` 新增 3 参构造注入 `BufferedReader in`。
- **`SessionMenu` 删除**（其职责并入 `ConsolePort.selectMenu/truncate` 与 `SessionReplayer`），对应 `SessionMenuTest` 并入 `ConsolePortTest`。

### 3.3 装配层（mycc-agent + mycc-app）

- **`AgentLoop` 显式会话绑定**：
  - 6 参构造（无 storage）保留：`session = Session.create()`。
  - **移除 7 参构造的「storage 非空 → `storage.latest()` 自动恢复」逻辑**；新增 8 参构造接收显式 `Session session` + `SessionStore storage`。
  - `withToolRegistry` 新增 7 参重载（`port, provider, toolRegistry, model, maxIterations, storage, session`）承载上述显式绑定；**原 6 参 storage-only 重载删除**（其自动恢复语义被本次设计取代）。
  - `run()` 的 `finally { storage.save(session) }` 落盘行为不变。
- **`AgentDemoApp` 编排**：
  1. 取 `store.list()`：为空 → `Session.create()`；非空 → 经 `SessionPicker.selectMenu(store)`（实现为 ConsolePort）选择。
  2. 用 `SessionReplayer.replay(session, port)` 把历史回放为与实时一致的事件流。
  3. 以显式 session 构造 `AgentLoop`，进入原有聊天循环；每轮用户输入先以 `USER` 事件交给渲染端口再驱动 agent，每轮 `run` 结束照旧落盘。

---

## 4. 错误处理

- 非法选择 / 空输入 → 提示并重读；Ctrl+D → 视为新对话。
- 反序列化损坏 JSON → 沿用现有 `MyccException` 上抛（已知风险，不新做容错）。
- 空会话 / 无 USER 消息 / 会话文件缺失各自有兜底（见 3.1 / 3.2）。

---

## 5. 测试计划（JUnit5 + AssertJ，先写失败测试）

| 层 | 测试 |
| --- | --- |
| FileStorage | 写入后 `lastModified` 有值且单调不减；不存在的 key 返回空 |
| SessionStore | `list()` 按最后修改倒序；title=最后一条 USER 消息；无 USER 消息用 `（空对话）`；`session/latest` 指针不入列；空目录返回空列表 |
| ConsolePort | `USER` 事件渲染 `我 > `；思考块隐藏/开启/收块；`selectMenu` 选历史/「新的对话」/非法重读/Ctrl+D/标题截断；未注入输入流抛异常（`SessionMenuTest` 并入） |
| SessionReplayer | 完整历史回放为 USER/TOOL_CALL/TOOL_RESULT/TOKEN/DONE；工具调用文本走 `ToolCallFormatter`；sessionId/seq 从 0 绑定；空会话仅 DONE；空白助手内容跳过 |
| AgentLoop | 显式传 session 后 `session()` 用该会话；移除自动恢复后仅 6 参构造建新会话 |
| AgentLoopPersistence | 改 `resumesLatestSessionOnConstructionAndContinues` → 显式传入 `old` 会话，语义从「自动恢复」改为「显式续聊」 |

---

## 6. 改动文件清单

| 文件 | 动作 |
| --- | --- |
| `mycc-ui/.../ui/OutputEventType.java` | 新增 `USER` 事件类型（实时提问与历史回放统一渲染） |
| `mycc-storage/.../spi/Storage.java` | 加 `lastModified(String)` |
| `mycc-storage/.../file/FileStorage.java` | 实现 `lastModified` |
| `mycc-agent/.../storage/SessionStore.java` | 加 `SessionSummary` + `list()` |
| `mycc-agent/.../loop/AgentLoop.java` | 去自动恢复，新增显式 session 构造/工厂 |
| `mycc-agent/.../session/SessionPicker.java` | 新增（历史会话选择端口，依赖倒置） |
| `mycc-agent/.../loop/SessionReplayer.java` | 新增（历史回放为与实时一致的事件流） |
| `mycc-agent/.../loop/ToolCallFormatter.java` | 新增（历史/实时工具调用文本统一格式） |
| `mycc-app/.../app/ConsolePort.java` | 实现 `InteractionPort` + `SessionPicker` 双端口，吸收菜单选择与标题截断 |
| `mycc-app/.../app/SessionMenu.java` | 删除（职责并入 ConsolePort + SessionReplayer） |
| `mycc-app/.../app/AgentDemoApp.java` | 经 `SessionPicker` 选会话、`SessionReplayer` 回放历史、实时输入以 `USER` 事件渲染 |
| 对应测试文件 | 新增 / 更新（见第 5 节；`SessionMenuTest` 并入 `ConsolePortTest`） |

---

## 7. 验证

- `mvn clean install` 全绿（126 用例 = 28 core + 21 tools + 10 ai + 21 storage + 34 agent + 12 app，目标不回归）。
- 手动跑 `AgentDemoApp`：造两段历史 → 重启经 `SessionPicker` 弹菜单选择、`SessionReplayer` 回放历史后续聊，上下文正确。