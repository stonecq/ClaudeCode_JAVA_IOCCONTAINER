# 项目 Spring 化重构：所有实例交给容器管理（设计文档）

- 版本：v0.1（设计稿，待分模块实现）
- 日期：2026-09-04
- 状态：设计已确认
- 目标：让自研 IoC 容器像 Spring 一样管理项目全部实例——补齐容器缺失能力（registerSingleton / 接口可匹配 / @Configuration·@Bean / prototype / 按名称查找），并把 `Main` 手工装配的全部实例迁移为注解/工厂方法驱动，`Main` 退化装配根为「开容器 → start → 执行命令 → close」。

---

## 1. 背景与目标

### 1.1 背景

用户诉求（原话）：「我要对项目进行重构…我需要像 spring一样，将所有实例都交给 spring 控制」。前置诱因：对 M8 `PermissionHook` 在 `Main.java` 里手工 `new` + `postProcessAfterInitialization(...)` 的装配方式不满（「如果要手动构造，那我们使用注解的意义在哪里」）。

现状根因：应用装配根是 `Main`——近 10 个实例靠手工 `new`（Terminal/PrintWriter/LineReader/CliPort/SessionStore/ConfigService/CliPermissionPrompt/PermissionPolicy/PermissionHook/HookDispatcher/OpenAiCompatProvider/CliContext）；`ToolRegistry` / `HookRegistry` 是 `DefaultIocContainer` 的 BeanPostProcessor 实例字段而非 bean；容器自身缺 registerSingleton、接口可匹配解析、@Configuration/@Bean、prototype 作用域、按名称查找。

### 1.2 目标

1. 容器补齐六项能力（详见 §3），使任意实例都能被「扫描 / 注解 / 工厂方法」纳管。
2. 构造/工厂签名全部保留，既有测试零回归（唯一例外：`MyccApplication` 增加 `start()` 拆分）。
3. `Main` 瘦身为启动器：env 预检（仅聊天命令缺 key）→ `new MyccApplication("com.learn.mycc")`（仅 create+register）→ `start()`（预创建单例，prototype 按需）→ picocli `getBean(命令)` → `close()`（Terminal.close 经 @Bean destroyMethod）。

### 1.3 非目标（YAGNI 红线）

本轮只做「Spring 化依赖注入与装配」：不引入 @Value 属性绑定、setter 注入、AOP、@PostConstruct（沿用既有 `InitializingBean`/`DisposableBean`）、BeanDefinition 注册 API 扩展、Session 纳管（维持调用侧值对象）。

---

## 2. 已确认的交互决策（用户拍板）

| 问题 | 决策 |
| --- | --- |
| 推进方式 | **先出设计文档**，确认大方向后分模块执行（S0→S3） |
| 会话级对象建模 | **prototype 作用域**（AgentLoop / ReplLoop 每次按需创建；Session 为调用侧值对象入参绑定） |
| 容器补齐能力（全选） | ① 基础注入（registerSingleton + 接口→实现可匹配 + 生命周期）② @Configuration/@Bean ③ prototype ④ 按名称查找 |
| 运行时对象（Terminal/PrintWriter/LineReader/LLM provider） | **@Bean 工厂方法进容器** |
| 接口多实现歧义 | 精确命中（单例→定义）→ 可匹配唯一 → 多个抛「多个可匹配」/ 缺失抛「未注册」；@Named 按名称消解 |
| provider 缺 key | Main 只读命令免 key；聊天命令缺 key 预检引导退出非 0；容器内 UnavailableLlmProvider 兜底（chat 抛错而非 NPE） |

---

## 3. 容器能力补齐（mycc-core，纯机制）

### 3.1 新增注解（`core/annotation`）

| 注解 | 目标/保留期 | 语义 |
| --- | --- | --- |
| `@Configuration` | TYPE / RUNTIME，元注解 `@Component` | 标记配置类；其 `@Bean` 方法在注册时展开为工厂 bean |
| `@Bean` | METHOD / RUNTIME | `name() default ""`（缺省方法名，作为 bean 名）；`destroyMethod() default ""`（close() 反射调用，如 Terminal.close） |
| `@Scope` | TYPE+METHOD / RUNTIME | `ScopeType value() default SINGLETON`；prototype 每次 getBean 新建不缓存 |
| `ScopeType` | 枚举 | `SINGLETON` / `PROTOTYPE` |
| `@Named(String)` | 类 / 构造器形参 / 字段 / 方法参数 | 按名称限定注入 / 类级 bean 名下覆盖；多实现歧义消解 |

### 3.2 BeanFactory 扩展（核心，`core/bean/BeanFactory.java`）

1. `registerSingleton(Class, Object)`：null 抛错；类型已有注册定义抛「重复注册」；写入 `singletons`，不记 `creationOrder`（外部注入对象不参与生命周期）。
2. **接口可匹配回退**：`getBean(type)` 精确命中（singletons→definitions）之后，追加 assignable candidates（singletons 按 `isInstance` + definitions 按 `isAssignableFrom` 惰性实例化，身份去重）；>1 抛「多个可匹配」fail-fast；0 抛「未注册 Bean 类型: X」。
3. **按名称查找**：新增 `getBean(String)`；`definitionsByName`（decapitalize 类名 / @Bean.name / @Named 类级名）；注入点带 @Named 按名称解析。
4. **prototype**：`createBean` 末尾 scope==PROTOTYPE 跳过 `singletons.put` 与 `creationOrder.add`（仍执行 invokeInit + BPP）；`preInstantiateSingletons` 跳过 prototype 定义（按需创建）。
5. **@Bean 工厂方法**：`instantiate` 分支先 `getBean(ownerConfigType)` 拿配置单例，再按方法参数 `resolveDependencies`（@Named 感知）反射调用（setAccessible）；产物走标准生命周期（init + BPP + 缓存/skip-prototype）；`close()` 逆序销毁既有 `DisposableBean.destroy` 并追加 @Bean `destroyMethod` 反射调用。
6. `getBean(Class, Object... args)`（prototype 绑定缝）：原型创建时构造参数先按位置用 `args[i]`（类型匹配），未命中则按类型/按名称从容器解析；单例已存在则忽略 args 返回现有。用于 `AgentLoop(…, session)` 绑已加载会话、`ReplLoop(port, runner, input, sessionId)` 全参覆盖。

### 3.3 容错与展开（`core/context/IocContainer.java`）

- 接口新增 `registerSingleton(Class,Object)` / `getBean(String)` / `<T> T getBean(Class<T>, Object...)`；impl 委托 BeanFactory。
- `DefaultIocContainer` ctor 追加 `registerSingleton(ToolRegistry/HookRegistry/IocContainer-自身)`，三者按类型可注入（CliContext 等经容器做 prototype 绑定）。
- `register(String)` / `register(Class...)` / `register(BeanDefinition...)` 汇总后额外展开：`@Configuration` 类型的每个 `@Bean` 方法注册为 `BeanDefinition.fromFactoryMethod`。
- `AnnotationScanner` 过滤条件增加 `|| isAnnotationPresent(Configuration.class)`（isAnnotationPresent 不穿越元注解，需显式匹配）。

### 3.4 权限默认实现（core，纯机制兜底）

- `PermissionPolicy` 加 `@Component`（无参无状态）。
- 新 `UnavailableUserConfirmation`：`@Component`，`prompt` 恒返 `ConfirmChoice.UNAVAILABLE`（fail-closed），供无 UI/裸容器装配时成为 `UserConfirmation` 唯一候选。

---

## 4. Bean 归属总表（现状手工 ↔ 容器后归属）

| 现状手工 / 缺失 | 归属 | 说明 |
| --- | --- | --- |
| `SessionStore new` | `@Component`（agent）`@Inject Storage` | Storage → 可匹配唯一实现 FileStorage |
| `FileStorage new` | `StorageConfig.@Bean FileStorage`（storage） | `FileStorage.defaultDirectory()` |
| `ConfigService new`（×2） | `@Component`（storage，无参构造） | 默认 `~/.mycc/config` |
| `CliPort new` | `CliConfig.@Bean CliPort` | 依赖 Terminal/PrintWriter/ConfigService |
| `LineReader new` | `CliConfig.@Bean LineReader` | 依赖 Terminal |
| `LineInput`（ReplLoop 适配） | `CliConfig.@Bean LineInput` | `ReplLoop.fromLineReader(reader)` |
| `PrintWriter / Terminal new` | `CliConfig.@Bean` Terminal(destroyMethod=close) / PrintWriter | JLine 运行时 |
| `UserConfirmation(cliPrompt) new` | `CliConfig.@Bean UserConfirmation` | 按接口返回类型精确命中，遮蔽默认 Unavailable（可匹配候选） |
| `PermissionPolicy new` | `@Component`（core） | — |
| `PermissionHook new`+手工 BPP | `@Component`（hooks） | 4 参单构造规则③自动注入；移除手工 `postProcessAfterInitialization` |
| `HookDispatcher new` | `@Component`（core）`@Inject HookRegistry` | HookRegistry 已 registerSingleton |
| `ToolRegistry / HookRegistry`（非 bean） | `registerSingleton`（DefaultIocContainer ctor） | BPP 实例字段 → 可按类型注入 |
| `OpenAiCompatProvider new` | `AiConfig.@Bean LlmProvider`（ai） | 读 OPENCODE_KEY；缺 → UnavailableLlmProvider |
| `CliContext new`（8/9 参） | `CliConfig.@Bean CliContext` | 既有 4 构造原样保留 + `setContainer` |
| `AgentLoop withToolRegistry new` | `AgentConfig.@Bean @Scope(PROTOTYPE)`（agent） | 依赖全注入，`Session session` 为形参，调用侧 `getBean(AgentLoop.class, session)` 绑定 |
| `ReplLoop new` | `CliConfig.@Bean @Scope(PROTOTYPE)` | `getBean(ReplLoop.class, port, runner, input, sessionId)` 全参覆盖 |
| `Session.create()/load()` | 调用侧值对象，不入容器 | 真正的隔离由 prototype AgentLoop 承载 |
| 命令（MyccCommand 等 5 个） | `@Component`（cli）`@Inject CliContext` | 单构造 → 规则③ |

`ToolRegistry` / `HookRegistry` 作为 BPP 的机制不变；`@Hook` / `@Tool` 扫描对容器创建的每个 bean 一律生效（含 @Bean 产物）。约束：**@Tool 只放单例 bean**，prototype 重复创建会触发 ToolRegistry 去重抛错。

---

## 5. 分模块改动清单

### mycc-core（容器能力 + 纯机制）
- `annotation/`：新增 `Configuration`、`Bean`、`Scope`、`ScopeType`、`Named`。
- `bean/BeanDefinition.java`：scope 字段 + 工厂方法模式 + `fromFactoryMethod(...)`。
- `bean/BeanFactory.java`：§3.2 六项能力。
- `context/IocContainer.java`：接口 + DefaultIocContainer（registerSingleton 注册表、自注入、register 展开 @Configuration）。
- `scan/AnnotationScanner.java`：匹配 @Configuration。
- `permission/PermissionPolicy.java`：加 @Component；`permission/UnavailableUserConfirmation.java`：新建 @Component。
- `hook/HookDispatcher.java`：加 @Component（+@Inject HookRegistry，若需）。

### mycc-storage
- `config/ConfigService.java`：加 @Component；新 `config/StorageConfig.java`：@Configuration，`@Bean FileStorage fileStorage()`。

### mycc-ai
- 新 `config/AiConfig.java`：@Configuration，`@Bean LlmProvider llmProvider(PrintWriter out)`。
- 新 `provider/UnavailableLlmProvider.java`：chat 时抛 `MyccException("未设置环境变量 OPENCODE_KEY…")`。

### mycc-hooks
- `hooks/PermissionHook.java`：加 @Component（headless 分支保留；不引 mycc-storage，注入接口靠可匹配落实现）。

### mycc-agent
- 新 `config/AgentConfig.java`：@Configuration，`@Bean @Scope(PROTOTYPE) AgentLoop agentLoop(ToolRegistry, LlmProvider, ConfigService, SessionStore, CliPort, HookDispatcher, Session session)`，内部 `AgentLoop.withToolRegistry(port, provider, registry, config.get("model","deepseek-v4-flash"), Integer.parseInt(config.get("maxIterations","10")), store, session, hooks)`。
- `storage/SessionStore.java`：加 @Component + @Inject Storage。

### mycc-cli
- 新 `config/CliConfig.java`：@Configuration，`@Bean` Terminal(destroyMethod=close)/PrintWriter/LineReader/LineInput/CliPort/UserConfirmation/@Scope(PROTOTYPE) ReplLoop/（@Bean CliContext，注入 8 依赖 + IocContainer，调 `setContainer`）。
- `CliContext.java`：保留 4 构造，新增 `setContainer(IocContainer)`；`enterRepl` 容器分支走 `getBean(AgentLoop.class, session)` + `getBean(ReplLoop.class, port, agent::run, input, session.id())`；否则走既有直接装配（测试路径不变）。
- `command/*.java`（5 个）：加 @Component。

### mycc-app
- `app/MyccApplication.java`：`assemble()` 去掉 `start()`（ctor=create+register 仅）；新增 `public void start()`。
- `app/Main.java`：删全部手工装配块 → env 预检（精简）→ `new MyccApplication().start()` → `CommandLine(getBean(命令)+子命令)` → `getBean(CliPort.class).writer().flush()` → `System.exit(code)` → `close()`。

---

## 6. 决策要点

- **UserConfirmation** 用 @Bean（返回类型=接口）精确命中，遮蔽 Unavailable 可匹配候选 → 无二义；裸容器回退 Unavailable fail-closed。
- **Session 是值对象**：调用侧 `Session.create()/store.load(id)` 创建并入参绑定；容器真正隔离的是携依赖的 prototype AgentLoop/ReplLoop。
- **prototype @Bean 形参**（AgentLoop 的 Session、ReplLoop 的 AgentRunner/sessionId）仅 getBean(type,args) 覆盖填充；preInstantiate 跳过 prototype；`getBean(type)` 裸调抛「无法解析参数」引导用 args 版。
- **model/maxIterations 硬编码下沉**：`"deepseek-v4-flash"/10` 移入 ConfigService key（model/maxIterations），由 @Bean / AgentConfig 组装时读取。

---

## 7. 里程碑（先设计文档，后分模块执行）

- **S0 容器能力（core）**：注解 + BeanFactory + IocContainer + scanner 六项能力；BeanFactoryTest / IocContainerTest TDD；`mvn -pl mycc-core -am test` 绿。
- **S1 权限/存储/钩子注解化**：PermissionPolicy/Unavailable/HookDispatcher/PermissionHook/ConfigService/SessionStore @Component + StorageConfig + registerSingleton 注册表 + MyccApplication.start() 拆出 + Main 删 PermissionHook 手工块；`mvn -pl mycc-app -am test` 绿。
- **S2 CLI/AI/Agent 装配迁移**：CliConfig/AiConfig/AgentConfig + 命令 @Component + CliContext.setContainer + Main 全瘦身；`mvn clean install` 全量绿。
- **S3 文档 + 验收**：设计文档（本文）落定 + 更新 v2 开发计划 / design-standards + dev-log；人工 jar 验收。

---

## 8. 验证

1. `mvn -pl mycc-core -am test` —— S0 全绿。
2. `mvn -pl mycc-app -am test` —— S1/S2 覆盖 core+hooks+storage+cli+agent；MyccApplicationTest 补 `.start()`。
3. `mvn clean install` 全量验收（预期 ≥198 用例，无删减回归）。
4. 人工 `java -jar mycc-app/target/mycc-app.jar`（需 OPENCODE_KEY + TTY）：裸 mycc 新建会话进 REPL、resume 续聊、`bash(cmd="echo hi")` 触发 `[y/N/a]` 审批、只读命令免 key —— 语义与重构前一致。
5. 不提交 git（CLAUDE.md #7，用户确认后再提交）。

---

## 9. 风险与边界

- **接口可匹配回退**破坏性语义：getBean(接口)→多实现抛「多个可匹配」——实施时逐一定位既有测试（预期无，已核对既有用例走具体类）。
- **@Token 与 prototype 冲突**须文档写明；本轮无 prototype @Tool。
- **@Bean 返回类型唯一性**：同一类型不得有两个 bean 定义（register 时 putIfAbsent 抛错）；当前无冲突。
- 既有测试的**构造器/工厂全部保留**是硬约束，S1/S2 只增不改类签名（唯一例外：MyccApplication 加 `start()`）。
- 扫描类路径扩到 `com.learn.mycc` 已含全部模块，无需改 basePackage。

---

## 10. 实施记录（S0–S3，2026-09-07 完成）

### S0 容器能力（mycc-core）✅
- 注解/BeanFactory/IocContainer/scanner 六项能力落地，`BeanFactoryTest` 20 用例、`IocContainerTest` 9 用例全绿（core 67）。

### S1 权限/存储/钩子注解化 ✅
- `HookDispatcher`/`PermissionHook`/`ConfigService`/`SessionStore` 加 `@Component`；`UnavailableUserConfirmation` 已随 S0 提交；新 `StorageConfig.@Bean FileStorage`。
- `MyccApplication` 拆 `start()`；`Main` 删 PermissionHook 手工装配块，钩子从容器按类型取。
- **暴露并修复 BeanFactory 缺陷**：`resolveAssignable` 对已按具体类型实例化的单例重复 `createBean` → 接口注入误报「多个可匹配」。改经 `getBean(具体类型)` 复用缓存，并新增 `BeanFactoryTest.interfaceFallbackReusesExistingSingleton` 回归。

### S2 CLI/AI/Agent 装配迁移 ✅
- 新 `AiConfig`（`@Bean LlmProvider`，缺 key→`UnavailableLlmProvider` 兜底）、`AgentConfig`（`@Bean @Scope(PROTOTYPE) agentLoop`）、`CliConfig`（Terminal/PrintWriter/LineReader/LineInput/CliPort/UserConfirmation/REPL/CliContext 全 @Bean）与 5 命令 `@Component`；`CliContext.setContainer` + enterRepl 容器分支；`Main` 全瘦身。
- **偏差（相对本文）**：
  1. AgentConfig 工厂形参用 `InteractionPort`（可落 CliPort）而非本文示例的 `CliPort`——`AgentLoop.withToolRegistry` 实际签名依赖 mycc-ui，agent 模块不依赖 cli。
  2. `CliConfig.terminal()` 在 `System.console()==null` 时直接建 `DumbTerminal`——规避 JLine 在 Windows 无控制台探测原生终端 4-7s 延迟与线程残留（测试 31s 挂起 → 0.9s）。
  3. model/maxIterations 缺省常量在 `CliConfig` 与 `AgentConfig` 各读一次（分属两模块，未抽公共常量）。

### S3 文档 + 验收 ✅
- `mvn clean install` 全量绿（含新增 app 回归），只读命令 jar 冒烟与缺 key 引导退出码 1 人工验证；交互 REPL 审批路径留待用户 TTY+key 环境复核（受环境限制，见 dev-log）。

### S3 后续：消除装配双路径 + 注入替身能力（2026-09-07）
- `overrideSingleton(Class, Object)`：写入单例缓存、`getBean` 优先返回，遮蔽已有定义；`registerSingleton` 维持严格冲突契约。等价 Spring `@MockBean`，供测试替身/运行时替换。
- `CliContext.enterRepl` 收成**单一路径**（一律 `getBean(AgentLoop/ReplLoop, args)`，无手工直装分支）；`CliContext` 改纯 `@Component` + 字段注入（容器经 `@Inject IocContainer` 自引用），`CliConfig` 删去 `cliContext` @Bean。
- `ReplLoop` 转 prototype `@Component @Scope(PROTOTYPE)`（`CliConfig` 删 `replLoop` @Bean）；AgentLoop 仍为 `AgentConfig` 的 `@Bean @Scope(PROTOTYPE)` 工厂（封装 via withToolRegistry 装配逻辑）。
- 双 prototype 统一由「调用侧 `getBean(type, args)` 覆盖注入点」创建。
- `MyccCommandTest` 迁移到「真实容器 + overrideSingleton 替身」：建容器 → 替身 {ConfigService/SessionStore/CliPort/LineInput/LlmProvider} → 注册 {CliContext, HookDispatcher, AgentConfig, CliConfig} → `getBean(CliContext)`；测试驱动与生产完全同装配路径。新增 `MyccApplicationTest.enterReplContainerPathWiresPrototypes` 把生产原型 wiring 纳入回归。