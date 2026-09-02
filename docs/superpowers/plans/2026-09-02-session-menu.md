# 会话历史菜单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **提交纪律（CLAUDE.md #7）：** 计划含每任务末 Commit 步骤；执行时向用户确认提交节奏（每任务提交 / 阶段末统一提交）后再提交。

**Goal:** 进入应用时列出历史会话供选择（含「新的对话」），选择后打印该会话历史并继续在其中对话。

**Architecture:** 数据层为 `Storage` 加 `lastModified(key)`，`SessionStore` 加 `SessionSummary + list()`（过滤 `session/<id>.json` 按修改时间倒序，title=最后一条 USER 消息）；电路层把 `AgentLoop` 的「storage 非空自动恢复 latest」改为显式绑定 session；UI 层 `mycc-app` 新增 `SessionMenu`（编号菜单 + 非法输入重读 + Ctrl+D 兜底 + 角色标记打印历史），`AgentDemoApp` 编排「有历史→菜单选择→打印→续聊」链路。分层单向：mycc-storage → mycc-agent → mycc-app。

**Tech Stack:** Java 17 / Maven 多模块 / JUnit5 + AssertJ / Jackson。全 Windows 绝对路径操作，测试断言用 `System.lineSeparator()`（Windows 为 `\r\n`）。

**规格:** `docs/superpowers/specs/2026-09-02-session-menu-design.md`（已获批）。

**设计要点（来自规格）**
- 菜单仅在存在历史会话时出现；无历史直接开新会话。
- 「新的对话」恒为最后一号；非法输入重读；Ctrl+D 视为新会话。
- title 超约 20 字（codepoint 安全）截断加 `…`。
- 历史打印角色标记：USER `我 > `、ASSISTANT 带工具 `[工具] name(args)`、TOOL `[结果] `、助手正文 `助手 > `。

---

### Task 1: Storage SPI 增加 lastModified

**Files:**
- Modify: `V:\learn\work_space\learn\learn-my-cc\mycc-storage\src\main\java\com\learn\mycc\storage\spi\Storage.java`
- Modify: `V:\learn\work_space\learn\learn-my-cc\mycc-storage\src\main\java\com\learn\mycc\storage\file\FileStorage.java`
- Test: `V:\learn\work_space\learn\learn-my-cc\mycc-storage\src\test\java\com\learn\mycc\storage\file\FileStorageTest.java`

- [ ] **Step 1: 写失败测试**

在 `FileStorageTest` 类的空行后（`rejectsNulByte` 测试方法之后）追加两个测试方法：

```java
    @Test
    void lastModifiedReturnsEpochMillisForExistingKey() {
        storage.write("a.txt", "hello");
        assertThat(storage.lastModified("a.txt")).isPresent();
        assertThat(storage.lastModified("a.txt").get()).isLessThanOrEqualTo(System.currentTimeMillis());
    }

    @Test
    void lastModifiedEmptyForMissingKey() {
        assertThat(storage.lastModified("missing.txt")).isEmpty();
    }
```

- [ ] **Step 2: 运行验证失败**

Run: `mvn -pl mycc-storage -am test`
Expected: 编译失败 — `Storage` 未声明 `lastModified(String)`，`FileStorage` 无此方法。

- [ ] **Step 3: 实现最小代码**

`Storage.java` 接口末尾（`keys()` 之后）新增：

```java
    /** 返回 key 对应内容最后修改时间的 epoch 毫秒；内容不存在返回 {@link Optional#empty()}。 */
    Optional<Long> lastModified(String key);
```

`FileStorage.java` 的 `keys()` 方法之后新增：

```java
    @Override
    public Optional<Long> lastModified(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.getLastModifiedTime(target).toMillis());
        } catch (IOException e) {
            throw new MyccException("读取存储修改时间失败: " + key + " / " + e.getMessage(), e);
        }
    }
```

- [ ] **Step 4: 运行验证通过**

Run: `mvn -pl mycc-storage -am test`
Expected: BUILD SUCCESS，`FileStorageTest` 全绿（原 13 用例 + 新增 2 = 15）。

- [ ] **Step 5: Commit**

```bash
git add mycc-storage/src/main/java/com/learn/mycc/storage/spi/Storage.java mycc-storage/src/main/java/com/learn/mycc/storage/file/FileStorage.java mycc-storage/src/test/java/com/learn/mycc/storage/file/FileStorageTest.java
git commit -m "feat(storage): Storage.lastModified() 支持排序历史会话"
```

---

### Task 2: SessionStore.list() + SessionSummary

**Files:**
- Modify: `V:\learn\work_space\learn\learn-my-cc\mycc-agent\src\main\java\com\learn\mycc\agent\storage\SessionStore.java`
- Test: `V:\learn\work_space\learn\learn-my-cc\mycc-agent\src\test\java\com\learn\mycc\agent\storage\SessionStoreTest.java`

- [ ] **Step 1: 写失败测试**

在 `SessionStoreTest` 现有 `import` 后追加一行：

```java
import java.nio.file.attribute.FileTime;
```

在该测试类的 `saveWritesLatestPointer` 测试方法之后追加 4 个测试方法：

```java
    @Test
    void listReturnsSessionsOrderedByLastModifiedDesc() throws IOException {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session first = Session.create();
        first.addMessage(Message.user("first question"));
        store.save(first);
        Session second = Session.create();
        second.addMessage(Message.user("second question"));
        store.save(second);

        Files.setLastModifiedTime(tempDir.resolve("session").resolve(first.id() + ".json"), FileTime.fromMillis(1000L));
        Files.setLastModifiedTime(tempDir.resolve("session").resolve(second.id() + ".json"), FileTime.fromMillis(2000L));

        List<SessionStore.SessionSummary> list = store.list();
        assertThat(list).extracting(SessionStore.SessionSummary::id)
                .containsExactly(second.id(), first.id());
        assertThat(list).extracting(SessionStore.SessionSummary::title)
                .containsExactly("second question", "first question");
    }

    @Test
    void listTitleFallsBackToPlaceholderWithoutUserMessage() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.assistant("hello", List.of()));
        store.save(session);

        assertThat(store.list()).extracting(SessionStore.SessionSummary::title)
                .containsExactly("（空对话）");
    }

    @Test
    void listExcludesLatestPointerAndEmptyWhenNothingSaved() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        assertThat(store.list()).isEmpty();

        Session session = Session.create();
        session.addMessage(Message.user("hi"));
        store.save(session);

        // save 会同时写出 session/latest 指针；不带 .json 后缀，不应入列
        assertThat(store.list()).hasSize(1);
    }

    @Test
    void listTitleUsesLastUserMessageNotFirst() {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.user("第一问"));
        session.addMessage(Message.assistant("答一", List.of()));
        session.addMessage(Message.user("第二问"));
        store.save(session);

        assertThat(store.list()).extracting(SessionStore.SessionSummary::title)
                .containsExactly("第二问");
    }
```

- [ ] **Step 2: 运行验证失败**

Run: `mvn -pl mycc-agent -am test`
Expected: 编译失败 — `SessionStore` 无 `SessionSummary` 类型与 `list()` 方法。

- [ ] **Step 3: 实现最小代码**

`SessionStore.java` 中，`latest()` 方法之后新增 `SessionSummary` record 与 `list()`，并在 `save` 之前的位置补上私有辅助方法：

```java
    /** 会话概况：id + 展示标题（最后一条用户消息）。 */
    public record SessionSummary(String id, String title) {}

    /** 列出所有历史会话概况，按最后修改时间倒序；session/latest 指针（无 .json 后缀）不入列。 */
    public List<SessionSummary> list() {
        return storage.keys().stream()
                .filter(k -> k.startsWith(PREFIX) && k.endsWith(SUFFIX))
                .sorted(Comparator.comparing((String k) -> storage.lastModified(k).orElse(0L)).reversed())
                .map(k -> new SessionSummary(idOf(k), lastUserMessageOf(sessionOf(k))))
                .toList();
    }

    private static String idOf(String key) {
        return key.substring(PREFIX.length(), key.length() - SUFFIX.length());
    }

    private Session sessionOf(String id) {
        return load(id).orElseGet(Session::create);
    }

    private static String lastUserMessageOf(Session session) {
        return session.conversation().messages().stream()
                .filter(m -> m.role() == ChatMessage.Role.USER)
                .reduce((first, second) -> second)
                .map(Message::content)
                .orElse("（空对话）");
    }
```

`SessionStore.java` 顶部 import 区块更新：

```java
import com.learn.mycc.ai.model.ChatMessage;
import java.util.Comparator;
```

（已有 `import java.util.List;`、`Optional`，维持不变。）

- [ ] **Step 4: 运行验证通过**

Run: `mvn -pl mycc-agent -am test`
Expected: BUILD SUCCESS，`SessionStoreTest` 全绿（原 8 用例 + 新增 4 = 12），`AgentLoopTest` 等其余 agent 测试不受影响。

- [ ] **Step 5: Commit**

```bash
git add mycc-agent/src/main/java/com/learn/mycc/agent/storage/SessionStore.java mycc-agent/src/test/java/com/learn/mycc/agent/storage/SessionStoreTest.java
git commit -m "feat(agent): SessionStore.list() 提供历史会话概况（倒序+标题）"
```

---

### Task 3: AgentLoop 显式会话绑定（去自动恢复）

**Files:**
- Modify: `V:\learn\work_space\learn\learn-my-cc\mycc-agent\src\main\java\com\learn\mycc\agent\loop\AgentLoop.java`
- Test: `V:\learn\work_space\learn\learn-my-cc\mycc-agent\src\test\java\com\learn\mycc\agent\loop\AgentLoopPersistenceTest.java`

- [ ] **Step 1: 写失败测试**

在 `AgentLoopPersistenceTest` 中：
- 用例 `resumesLatestSessionOnConstructionAndContinues` 更名为 `continuesInExplicitSelectedSession`，并把构造调用改为显式传 `old` 会话（删除「明确不自动恢复最新」的旧断言语义）：

```java
    @Test
    void continuesInExplicitSelectedSession() {
        FileStorage fileStorage = new FileStorage(tempDir);
        SessionStore store = new SessionStore(fileStorage);
        Session old = Session.create();
        old.addMessage(Message.user("旧问题"));
        old.addMessage(Message.assistant("旧回答", List.of()));
        store.save(old);

        AtomicReference<List<ChatMessage>> seen = new AtomicReference<>();
        MockProvider provider = MockProvider.scripted(request -> {
            seen.set(request.messages());
            return ChatResponse.text("新回答");
        });
        RecordingPort port = new RecordingPort();

        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, new ToolRegistry(), "mock", 10, store, old);

        assertThat(agent.session().id()).isEqualTo(old.id());
        assertThat(agent.session().conversation().messages()).hasSize(2);

        String result = agent.run("新问题");

        assertThat(result).isEqualTo("新回答");
        assertThat(seen.get()).extracting(ChatMessage::content)
                .containsExactly("旧问题", "旧回答", "新问题");
        assertThat(agent.session().conversation().messages()).hasSize(4);
        assertThat(store.latest().orElseThrow().conversation().messages()).hasSize(4);
    }
```

- 用例 `persistsSessionAfterRun` 的构造改为显式传新建会话：

```java
        Session session = Session.create();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, new ToolRegistry(), "mock", 10, store, session);
```

- 追加新用例 `bindsExplicitSessionEvenWhenLatestExists`（证明不再自动恢复 latest）：

```java
    @Test
    void bindsExplicitSessionEvenWhenLatestExists() {
        FileStorage fileStorage = new FileStorage(tempDir);
        SessionStore store = new SessionStore(fileStorage);
        Session old = Session.create();
        old.addMessage(Message.user("旧问题"));
        old.addMessage(Message.assistant("旧回答", List.of()));
        store.save(old);

        Session fresh = Session.create();
        MockProvider provider = MockProvider.scripted(request -> ChatResponse.text("回答"));
        RecordingPort port = new RecordingPort();
        AgentLoop agent = AgentLoop.withToolRegistry(port, provider, new ToolRegistry(), "mock", 10, store, fresh);
        agent.run("新问题");

        assertThat(agent.session().id()).isEqualTo(fresh.id());
        assertThat(agent.session().conversation().messages()).extracting(Message::content)
                .containsExactly("新问题", "回答");
    }
```

- [ ] **Step 2: 运行验证失败**

Run: `mvn -pl mycc-agent -am test`
Expected: 编译失败 — `withToolRegistry` 无 7 参（含 `Session`）重载。

- [ ] **Step 3: 实现最小代码**

`AgentLoop.java` 改动：

**构造（替换现有两个构造）：**

```java
    public AgentLoop(InteractionPort port, LlmProvider provider, ToolCallExecutor executor,
                     List<ToolSpec> tools, String model, int maxIterations) {
        this(port, provider, executor, tools, model, maxIterations, null, Session.create());
    }

    /** @param storage 会话存储；null 表示不持久化。@param session 已绑定会话（续聊/新对话由调用方选定）。 */
    public AgentLoop(InteractionPort port, LlmProvider provider, ToolCallExecutor executor,
                     List<ToolSpec> tools, String model, int maxIterations, SessionStore storage, Session session) {
        this.port = port;
        this.provider = provider;
        this.executor = executor;
        this.tools = List.copyOf(tools);
        this.model = model;
        this.maxIterations = maxIterations;
        this.storage = storage;
        this.session = session;
    }
```

（删除原 7 参 `SessionStore storage` 构造及其 `storage.latest().orElseGet(...)` 自动恢复逻辑。）

**工厂（替换原两参工厂）：**

```java
    /** 从工具注册表装配：不持久化、新建会话。 */
    public static AgentLoop withToolRegistry(InteractionPort port, LlmProvider provider,
                                             ToolRegistry toolRegistry, String model, int maxIterations) {
        return withToolRegistry(port, provider, toolRegistry, model, maxIterations, null, Session.create());
    }

    /** 从工具注册表装配并绑定指定会话；storage 为 null 表示不持久化。 */
    public static AgentLoop withToolRegistry(InteractionPort port, LlmProvider provider,
                                             ToolRegistry toolRegistry, String model, int maxIterations,
                                             SessionStore storage, Session session) {
        ParameterSchemaGenerator schemaGenerator = new ParameterSchemaGenerator();
        List<ToolSpec> specs = toolRegistry.getAll().stream()
                .map(definition -> new ToolSpec(definition.getName(), definition.getDescription(),
                        schemaGenerator.generate(definition.getMethod())))
                .toList();
        return new AgentLoop(port, provider, new ToolCallExecutor(toolRegistry), specs, model, maxIterations, storage, session);
    }
```

（删除原 6 参 `SessionStore storage` 工厂重载。）`run()` 的 `finally { storage.save(session) }` 与类注释保持不变，但类 Javadoc 中「注入后启动时自动恢复最近会话」一句改为「注入后每轮 run 结束落盘，会话由调用方显式绑定」。

- [ ] **Step 4: 运行验证通过**

Run: `mvn -pl mycc-agent -am test`
Expected: BUILD SUCCESS，`AgentLoopPersistenceTest` 3 用例 + `AgentLoopTest` 原 5 用例全绿。

- [ ] **Step 5: Commit**

```bash
git add mycc-agent/src/main/java/com/learn/mycc/agent/loop/AgentLoop.java mycc-agent/src/test/java/com/learn/mycc/agent/loop/AgentLoopPersistenceTest.java
git commit -m "refactor(agent): AgentLoop 显式绑定会话，移除 latest 自动恢复"
```

---

### Task 4: SessionMenu + AgentDemoApp 接入 + 全量验证

**Files:**
- Create: `V:\learn\work_space\learn\learn-my-cc\mycc-app\src\main\java\com\learn\mycc\app\SessionMenu.java`
- Create: `V:\learn\work_space\learn\learn-my-cc\mycc-app\src\test\java\com\learn\mycc\app\SessionMenuTest.java`
- Modify: `V:\learn\work_space\learn\learn-my-cc\mycc-app\src\main\java\com\learn\mycc\app\AgentDemoApp.java`
- Modify: `V:\learn\work_space\learn\learn-my-cc\dev-log\2026-09-02.md`

- [ ] **Step 1: 写失败测试（SessionMenuTest）**

新建 `V:\learn\work_space\learn\learn-my-cc\mycc-app\src\test\java\com\learn\mycc\app\SessionMenuTest.java`：

```java
package com.learn.mycc.app;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMenuTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    private final String nl = System.lineSeparator();

    private SessionStore storeWith(String title) {
        SessionStore store = new SessionStore(new FileStorage(tempDir));
        Session session = Session.create();
        session.addMessage(Message.user(title));
        store.save(session);
        return store;
    }

    private static BufferedReader reader(CharSequence input) {
        return new BufferedReader(new StringReader(input.toString()));
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void selectsExistingSessionByIndex() {
        SessionMenu menu = new SessionMenu(storeWith("第一段对话"), out, reader("1\n"));

        Session session = menu.select();

        assertThat(session.conversation().messages()).extracting(Message::content)
                .containsExactly("第一段对话");
    }

    @Test
    void selectsNewConversationOption() {
        SessionStore store = storeWith("一段历史");
        SessionMenu menu = new SessionMenu(store, out, reader("2\n"));

        Session session = menu.select();

        assertThat(session.isEmpty()).isTrue();
    }

    @Test
    void repromptsOnInvalidInputThenSucceeds() {
        SessionMenu menu = new SessionMenu(storeWith("历史"), out, reader("abc\n2\n"));

        Session session = menu.select();

        assertThat(session.isEmpty()).isTrue();
        assertThat(output()).contains("无效选择");
    }

    @Test
    void returnsFreshSessionOnCtrlD() {
        BufferedReader eof = new BufferedReader(new StringReader(""));
        SessionMenu menu = new SessionMenu(storeWith("历史"), out, eof);

        Session session = menu.select();

        assertThat(session.isEmpty()).isTrue();
    }

    @Test
    void truncatesLongTitlesWithEllipsis() {
        SessionMenu menu = new SessionMenu(
                storeWith("abcdeabcdeabcdeabcdeabcde"), out, reader("1\n"));

        menu.select();

        assertThat(output()).contains("1. abcdeabcdeabcdeabcde…");
    }

    @Test
    void printHistoryPrintsRoleMarkers() {
        Session session = Session.create();
        session.addMessage(Message.user("读一下文件"));
        session.addMessage(Message.assistant("", List.of(new ToolCall("c1", "read_file", "{\"path\":\"a.txt\"}"))));
        session.addMessage(Message.tool("c1", "文件内容"));
        session.addMessage(Message.assistant("已读取", List.of()));

        new SessionMenu(new SessionStore(new FileStorage(tempDir)), out, reader("")).printHistory(session);

        assertThat(output()).isEqualTo(
                "我 > 读一下文件" + nl
                        + "[工具] read_file({\"path\":\"a.txt\"})" + nl
                        + "[结果] 文件内容" + nl
                        + "助手 > 已读取" + nl);
    }

    @Test
    void printHistoryForEmptySessionPrintsPlaceholder() {
        new SessionMenu(new SessionStore(new FileStorage(tempDir)), out, reader("")).printHistory(Session.create());

        assertThat(output()).isEqualTo("（新会话）" + nl);
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `mvn -pl mycc-app -am test`
Expected: 编译失败 — `SessionMenu` 类不存在。

- [ ] **Step 3: 实现 SessionMenu**

新建 `V:\learn\work_space\learn\learn-my-cc\mycc-app\src\main\java\com\learn\mycc\app\SessionMenu.java`：

```java
package com.learn.mycc.app;

import com.learn.mycc.agent.session.Message;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.model.ToolCall;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * 启动时历史会话选择菜单 + 历史打印。编号由 1 开始，「新的对话」恒为最后一号。
 * 仅在存在历史会话时由应用调用 {@link #select()}；非法输入重读，Ctrl+D 视为新会话。
 */
public final class SessionMenu {

    private static final int TITLE_LIMIT = 20;

    private final SessionStore store;
    private final PrintStream out;
    private final BufferedReader in;

    public SessionMenu(SessionStore store, PrintStream out, BufferedReader in) {
        this.store = store;
        this.out = out;
        this.in = in;
    }

    /** 让用户从历史会话中选择；返回已加载会话或新建会话。 */
    public Session select() throws IOException {
        List<SessionStore.SessionSummary> summaries = store.list();
        out.println("历史会话：");
        for (int i = 0; i < summaries.size(); i++) {
            out.println((i + 1) + ". " + truncate(summaries.get(i).title()));
        }
        out.println((summaries.size() + 1) + ". 新的对话");
        while (true) {
            out.print("请选择 > ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
                return Session.create();
            }
            int choice;
            try {
                choice = Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                out.println("无效选择，请重新输入。");
                continue;
            }
            if (choice >= 1 && choice <= summaries.size()) {
                return store.load(summaries.get(choice - 1).id()).orElseGet(Session::create);
            }
            if (choice == summaries.size() + 1) {
                return Session.create();
            }
            out.println("无效选择，请重新输入。");
        }
    }

    /** 按角色标记打印会话历史；空会话打印占位符。 */
    public void printHistory(Session session) {
        List<Message> messages = session.conversation().messages();
        if (messages.isEmpty()) {
            out.println("（新会话）");
            return;
        }
        for (Message m : messages) {
            switch (m.role()) {
                case USER -> out.println("我 > " + m.content());
                case ASSISTANT -> {
                    if (m.hasToolCalls()) {
                        out.println("[工具] " + formatToolCalls(m.toolCalls()));
                    }
                    if (!m.content().isBlank()) {
                        out.println("助手 > " + m.content());
                    }
                }
                case TOOL -> out.println("[结果] " + m.content());
                default -> { }
            }
        }
    }

    private static String truncate(String title) {
        if (title.codePointCount(0, title.length()) <= TITLE_LIMIT) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, TITLE_LIMIT)) + "…";
    }

    private static String formatToolCalls(List<ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(call -> call.name() + "(" + call.arguments() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `mvn -pl mycc-app -am test`
Expected: BUILD SUCCESS，`SessionMenuTest` 7 用例全绿。

- [ ] **Step 5: 修改 AgentDemoApp 接入菜单**

`V:\learn\work_space\learn\learn-my-cc\mycc-app\src\main\java\com\learn\mycc\app\AgentDemoApp.java`：

Import 区块增加一行（位于 `agent.loop.AgentLoop` 之后）：

```java
import com.learn.mycc.agent.session.Session;
```

将会话装配段（现为 `SessionStore store = ...` 到 `10, store);`）整体替换为：

```java
            SessionStore store = new SessionStore(FileStorage.defaultDirectory());
            Session session;
            if (store.list().isEmpty()) {
                session = Session.create();
            } else {
                SessionMenu menu = new SessionMenu(store, out, reader);
                session = menu.select();
                menu.printHistory(session);
            }
            boolean showReasoning = Boolean.parseBoolean(new ConfigService().get("showReasoning", "true"));
            AgentLoop agent = AgentLoop.withToolRegistry(
                    new ConsolePort(out, showReasoning),
                    provider,
                    container.getToolRegistry(),
                    "deepseek-v4-flash",
                    10,
                    store,
                    session);
```

（原「检测到历史会话，将自动恢复继续上次上下文。」提示已被菜单取代，删除。）

- [ ] **Step 6: 更新 dev-log**

在 `V:\learn\work_space\learn\learn-my-cc\dev-log\2026-09-02.md` 的「今日完成」追加（覆盖整段已有内容，不删除前面条目）：

```markdown
- [x] `Storage.lastModified(key)` + `SessionStore.list()`：历史会话概况（`SessionSummary`，按修改时间倒序，title=最后一条 USER 消息），过滤 `session/latest` 指针
- [x] `AgentLoop` 改为显式绑定 session（移除「storage 非空自动恢复 latest」），工厂新增 7 参重载
- [x] 新增 `SessionMenu`（编号菜单 + 约 20 字截断 + 「新的对话」项 + 非法输入重读/Ctrl+D 兜底）与 `printHistory`（角色标记 `我 >`/`[工具]`/`[结果]`/`助手 >`）
- [x] `AgentDemoApp` 接入：无历史直接新会话；有历史先选菜单、打印历史再续聊；每轮结束照旧落盘
```

「待办事项」保留 M6 条目不动。

- [ ] **Step 7: 全量构建验证**

Run: `mvn clean install`
Expected: BUILD SUCCESS，全部测试绿（原 106 + FileStorageTest +2、SessionStoreTest +4、SessionMenuTest +7、AgentLoopPersistenceTest 净 +1 = **120 用例**）。

- [ ] **Step 8: Commit**

```bash
git add mycc-app/src/main/java/com/learn/mycc/app/SessionMenu.java mycc-app/src/test/java/com/learn/mycc/app/SessionMenuTest.java mycc-app/src/main/java/com/learn/mycc/app/AgentDemoApp.java dev-log/2026-09-02.md
git commit -m "feat(app): 会话历史菜单 + 历史打印 + 显式续聊"
```

---

## 收尾：手动验证（非自动步骤）

```bash
mvn -pl mycc-app -am package -DskipTests
# 用已存在多条历史的 ~/.mycc 目录运行，观察菜单 → 选择 → 历史打印 → 续聊
```