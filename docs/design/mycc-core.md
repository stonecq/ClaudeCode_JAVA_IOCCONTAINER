# mycc-core 模块开发指南（新人上手）

> **适用对象**：初次接触本代码库的新人。
> **范围**：`mycc-core/src/main/java/com/learn/mycc/core`（下文简写为 `core/`）。
> **配套文档**：需求 PRD `docs/superpowers/specs/2026-08-27-mycc-prd.md`；IoC 重构设计 `docs/superpowers/specs/2026-09-04-spring-ioc-refactor-design.md`；模块边界 `docs/design/design-standards.md`；编码规范 `docs/standards/coding-standards.md`。
>
> 阅读方式：第 1、2 节建立心智模型，第 3 节细读，第 4 节把第 3 节串成一条完整调用链，第 5 节是回读路线，第 6 节是动手验证与避坑。

---

## 1. 项目概述

### 1.1 项目与模块定位

`mycc` 是从零实现的一个简易版 Claude Code（学习型 agent 工具）：有自研迷你 IoC 容器、可插拔 LLM、`@Tool` 工具系统、agent 循环、多界面适配（CLI 先行，Web 二期）。

`mycc-core` 是所有模块**最底层的机制层**，只提供与领域无关的通用能力：

- **IoC 容器**：`@Component` 扫描注册、`@Configuration`/`@Bean` 工厂方法、依赖注入、生命周期管理；
- **工具/钩子注册表**：自动收集 `@Tool` 方法与 `@Hook` 方法；
- **纯机制模型**：hook 事件、权限决策策略等，只定义"机制"，不绑定任何具体 UI / LLM / 存储。

**关键纪律（纯机制层）**：core 不知道 `ToolCall`、`LlmProvider`、CLI 终端等具体类型。需要跨模块协作时，它只依赖**接口**（如 `PermissionRuleStore`、`UserConfirmation`），由上层模块提供实现并注入。例如 `PermissionPolicy` 按"工具名字符串"决策，而不引入 ToolCall 类型（`core/permission/PermissionPolicy.java`）。

### 1.2 技术栈

| 项 | 选型 | 说明 |
| --- | --- | --- |
| 语言 / 运行时 | Java 17 | 用到 record、`Status: … pattern` 等特性 |
| 构建 | Maven 多模块 | root + mycc-core/ui/ai/tools/storage/agent/cli/app 等 |
| 测试 | JUnit5 + AssertJ | 测试优先，阶段验收 = 该阶段测试全绿 |
| IoC 实现 | **纯反射，零框架** | 无 Spring 依赖，全部用 `java.lang.reflect` 手写 |
| 外部依赖 | 核心零第三方 | hook 包用到 slf4j 打日志，其余基本无 |
| 反射辅助 | 0 个字节码代理 | 后置处理器只是"观察到就注册"，不改字节码 |

### 1.3 整体设计思路（四个支点）

1. **仿 Spring 的"声明-元数据-实例化"三件套**：
   - 注解是**声明**（`@Component`/`@Inject`/`@Bean`…）；
   - `BeanDefinition` 是**静态元数据**（类型、名字、作用域、怎么注入）——只管描述，不做实例化；
   - `BeanFactory` 是**动态执行者**（反射实例化、注入、生命周期）。
2. **面向接口 + 门面模式**：对外只暴露 `IocContainer` 接口，具体实现 `DefaultIocContainer`（同文件包私有）委托给 `BeanFactory`。调用方永远不直接 new BeanFactory。
3. **扩展点靠 BeanPostProcessor"被动捕获"**，而不是主动扫描 classpath：把 `ToolRegistry`/`HookRegistry` 注册成 Bean 后置处理器，**每个 Bean 创建完成后**顺手反射它上面的 `@Tool`/`@Hook` 方法。谁创建了、就扫谁，天然与容器解耦。
4. **依赖单向、无环**：annotation/exception 是叶子，其余包只向上依赖（详见 2.3）。

---

## 2. 目录与模块结构

### 2.1 目录树

```
mycc-core/src/main/java/com/learn/mycc/core/
├── annotation/   寄存全部注解与枚举（声明层）
│   ├── Component / Configuration / Bean / Inject / Named
│   ├── Scope + ScopeType           （作用域）
│   ├── Tool + ToolParam + ToolRisk （工具声明）
│   └── Hook                        （钩子声明）
├── bean/         IoC 实例化内核
│   ├── BeanDefinition              元数据（静态蓝图）
│   ├── BeanFactory                 实例化 + 注入 + 生命周期（核心内核）
│   ├── BeanPostProcessor           Bean 后置处理扩展点（接口）
│   ├── InitializingBean            初始化回调（接口）
│   └── DisposableBean              销毁回调（接口）
├── scan/         类路径扫描
│   └── AnnotationScanner           发现 @Component / @Configuration
├── context/      IoC 门面
│   └── IocContainer                接口 + DefaultIocContainer（同文件）
├── tool/         工具机制
│   ├── ToolRegistry                实现 BeanPostProcessor，收集 @Tool
│   ├── ToolDefinition              @Tool 方法元数据
│   └── ParameterSchemaGenerator    方法参数 → JSON Schema
├── hook/         钩子机制
│   ├── HookRegistry                实现 BeanPostProcessor，收集 @Hook
│   ├── HookDefinition / HookEvent / HookEventType / HookDecision
│   └── HookDispatcher              事件派发（任一段拒绝即短路）
├── permission/   权限决策机制（纯策略）
│   ├── PermissionPolicy / PermissionRuleStore / UserConfirmation
│   ├── UnavailableUserConfirmation（fail-closed 兜底）
│   └── PermissionDecision / PermissionVerdict
├── config/       通用组件
│   └── ApplicationConfig           @Component，工作区路径
└── exception/    框架异常
    └── MyccException               顶层运行时异常
```

测试目录：`mycc-core/src/test/java/com/learn/mycc/core/` 下与 main 保持同包结构；`bean/fixture/`、`context/fixture/` 是测试夹具（@Configuration 配置类、BeanPostProcessor 记录器、各种 Bean 依赖样例）。

### 2.2 各包职责速览

| 包 | 职责 | 典型类 / 关键注解 |
| --- | --- | --- |
| `annotation` | 全部声明注解与枚举，无业务逻辑 | 见 3.1 |
| `bean` | Bean 元数据 + 实例化内核 + 生命周期扩展点 | `BeanFactory`、`BeanDefinition`、`BeanPostProcessor` |
| `scan` | 把包路径下的类找出来并转成 BeanDefinition | `AnnotationScanner` |
| `context` | 对外门面，编排 scanner + factory + 注册表 | `IocContainer`、`DefaultIocContainer` |
| `tool` | 工具注册表与参数 Schema 生成 | `ToolRegistry`、`ParameterSchemaGenerator` |
| `hook` | 钩子注册表、事件模型与派发 | `HookRegistry`、`HookDispatcher` |
| `permission` | 权限决策策略（纯机制，按工具名决策） | `PermissionPolicy`、`UserConfirmation` |
| `config` | 通用可注入组件 | `ApplicationConfig` |
| `exception` | 框架统一异常 | `MyccException` |

### 2.3 包依赖关系（单向、无环）

```
                       ┌─────────────┐
                       │  context    │  ← 门面，依赖 bean/scan/tool/hook
                       └──────┬──────┘
        ┌───────────────────┬─┴───────────────┬───────────────────┐
        ▼                   ▼                 ▼                   ▼
  ┌──────────┐      ┌─────────────┐   ┌─────────────┐    ┌──────────────┐
  │   bean   │◄─────│    scan     │   │  tool / hook │   │  permission  │
  └────┬─────┘      └──────┬──────┘   └──────┬──────┘    └──────┬───────┘
       └────────┬──────────┘                  ▲                  │
                ▼                             │                  ▼
        ┌──────────────┐  ┌────────────────┐  │   ┌──────────────────────┐
        │  annotation   │  │   exception     │  │   │   config             │
        │  （叶子）      │  │  （叶子）       │  └──►│  （依赖 annotation）  │
        └──────────────┘  └────────────────┘      └──────────────────────┘
```

规则：**依赖永远指向叶子**（annotation / exception），不存在反向引用。`tool`/`hook` 都依赖 `bean`（注册表实现了 `BeanPostProcessor`）；`hook` 又依赖 `annotation`（`@Hook` 引用 `HookEventType`）。`context` 在最顶上编排所有人。新人迁移代码时保持"新增类落在正确包、依赖单向"即可。

---

## 3. 核心组件详解

### 3.0 先建立一个心智模型：四个角色

| 角色 | 代表 | 类比 |
| --- | --- | --- |
| **声明** | 注解 | "我说我要被管理 / 我要注入谁" |
| **蓝图** | `BeanDefinition` | 菜谱：一道菜需要哪些料、怎么做 |
| **厨子** | `BeanFactory` | 照着菜谱把菜做出来、摆上桌（缓存） |
| **前台** | `IocContainer` | 对外接单的入口：点菜（register）、上菜（getBean）、收摊（close） |
| **侦察兵** | `AnnotationScanner` | 扫描市场上有哪些备选食材（classpath 里的类） |

后文都围绕这五个角色展开。先看"声明"这一层。

### 3.1 注解全解（`core/annotation`）

所有注解 `@Retention(RUNTIME)` + `@Target` 指定用途，供反射读取。

**① 组件声明类**

| 注解 | 目标 | 作用 | 被谁识别 / 在哪个阶段 |
| --- | --- | --- | --- |
| `@Component` | 类 | 标记一个类是容器托管的组件 | `AnnotationScanner` 扫描过滤（注册阶段） |
| `@Configuration` | 类 | 配置类；内部 `@Bean` 方法会被展开为独立 Bean 定义。**以 `@Component` 为元注解**，配置类本身也是 Bean | 扫描器显式匹配 + `DefaultIocContainer.expandFactoryMethods` 展开（注册阶段） |
| `@Bean` | 方法 | 工厂方法：返回类型=Bean 注册键，方法名（或 `name()`）=Bean 名；`destroyMethod()` 指定关闭时反射调用 | 注册阶段展开，创建阶段反射调用 |

**② 依赖注入类**

| 注解 | 目标 | 作用 |
| --- | --- | --- |
| `@Inject` | 构造器 / 字段 | 声明注入点；多个 `@Inject` 构造器会被判为"不明确"抛异常；字段注入是兜底路径 |
| `@Named` | 类 / 构造器形参 / 字段 / 方法形参 | 类级：覆盖默认 Bean 名；注入点：按名称消解"接口多实现二义" |

**③ 作用域**

`@Scope`（类 / `@Bean` 方法）+ `ScopeType` 枚举：`SINGLETON`（默认，容器内唯一、按类型共享）vs `PROTOTYPE`（每次取用新建、不入缓存，用于会话级对象如 AgentLoop）。

**④ 工具 / 钩子**

| 注解 | 目标 | 作用 |
| --- | --- | --- |
| `@Tool` | 方法 | 声明可被 agent 调用的工具；`name()` 全局唯一、`description()` 注入 LLM Schema、`risk()` 默认 LOW | 创建阶段被 `ToolRegistry`（BPP）捕获 |
| `@ToolParam` | 方法形参 | 参数元数据：`description()` / `required()`，用于生成 JSON Schema |
| `@ToolRisk` | 枚举 | `LOW` / `HIGH`，HIGH（bash、写文件）默认触发审批 |
| `@Hook` | 方法 | 订阅某 `HookEventType` 生命周期事件；方法签名须为 `(HookEvent)` | 创建阶段被 `HookRegistry`（BPP）捕获 |

> **记住结论**：第④类注解和第二波扫描有关。扫描分两波，见 3.5 与 4 章。

### 3.2 `BeanDefinition`：静态蓝图（`core/bean/BeanDefinition.java`）

`final` 不可变类，只存元数据、**不做任何实例化**。一个实例记录：

- `type`（注册键=类）/ `name`（默认类简单名首字母小写，`@Named` 类级覆盖）/ `scope`（默认 SINGLETON）；
- **注入方式二选一**：`injectionConstructor`（构造器注入）或 `injectFields`（字段注入）；有构造器则字段恒为空，反之亦然；
- 工厂模式专属：`ownerConfigType` / `factoryMethod` / `destroyMethod`（非工厂 Bean 为 null）。

构建方式（静态工厂）：
- `BeanDefinition.from(Class)`：用于普通组件类。内部顺序：`@Named` 覆盖名 → `@Scope` 覆盖作用域 → `resolveInjectionConstructor` 找注入构造器 → 没有则 `resolveInjectFields` 收字段。
- `BeanDefinition.fromFactoryMethod(ownerConfigType, method, name, scope)`：用于 `@Bean` 工厂方法，注册键=方法返回类型，注入方式固定为空（工厂路径不走构造/字段注入）。

`resolveInjectionConstructor` 规则（对标 Spring）：
1. 多个 `@Inject` 构造器 → 抛"不明确"；
2. 恰好一个 `@Inject` 构造器 → 用它；
3. 无 `@Inject`、但只有一个带参构造器 → 也用它（唯一构造器兜底）；
4. 其余（无参 / 多构造器未标注）→ 返回 null，退回"无参构造 + 字段注入"。

`resolveInjectFields` 会**上溯父类**收集 `@Inject` 字段（到但不含 Object），所以能注入父类声明的依赖。

### 3.3 `BeanFactory`：实例化内核（`core/bean/BeanFactory.java`）

模块里最复杂的类，承担全部"动态"逻辑。内部有 6 张数据结构：

| 字段 | 结构 | 用途 |
| --- | --- | --- |
| `definitionsByType` | `LinkedHashMap<Class, BeanDefinition>` | 类型→蓝图；保序供预创建按序进行 |
| `definitionsByName` | `HashMap<String, BeanDefinition>` | 名字→蓝图；供 `getBean(String)` 与 `@Named` 注入 |
| `singletons` | `HashMap<Class, Object>` | 已创建的单例缓存（含 `registerSingleton` 外部注入） |
| `creating` | `IdentityHashMap` 包装的 Set | "正在创建中"集合，循环依赖检测 |
| `postProcessors` | `List<BeanPostProcessor>` | 按序应用的后置处理器 |
| `creationOrder` | `List<Class>` | 单例创建顺序，close 时逆序销毁 |

**核心入口与机制：**

- `register(...)`：双表防重。`definitionsByType.putIfAbsent` / `definitionsByName.putIfAbsent` 返回非 null 即抛"重复注册"。
- `registerSingleton(type, instance)`：外部注入对象直接进 `singletons`，**不记 `creationOrder`、不参与生命周期回调**。设计：容器构造时就 `registerSingleton(ToolRegistry/HookRegistry/IocContainer 自身)` 三件套，让这三个能按类型被 `@Inject`。
- `getBean(Class)` 三段式：**精确命中**（单例缓存 → 注册定义即时创建）→ **接口可匹配回退** `resolveAssignable`。
- `getBean(Class, Object... args)`：prototype 绑定缝。单例忽略 args 返回现有；prototype 把 args 按位置覆盖构造/工厂形参（如 `AgentLoop` 绑已加载 Session）。
- `getBean(String)`：按名字查 `definitionsByName`，命中后按类型解析。
- `resolveAssignable(Class)`：接口回退。收集两边候选——单例按 `isInstance`、定义按 `isAssignableFrom` 惰性实例化；用 IdentityHashMap 身份去重；**恰好一个**返回，>1 抛"多个可匹配"，=0 抛"未注册"。跳过 `creating` 中的类型防重入。
- `preInstantiateSingletons()`：启动时按注册顺序对所有 **SINGLETON** 定义 `getBean`；prototype 跳过（按需创建）。依赖经 `getBean` 递归，所以"被依赖方先创建、依赖方后创建"。
- `close()`：按 `creationOrder` **逆序**销毁（后创建先销毁）——先 `DisposableBean.destroy()`，工厂 Bean 再加 `@Bean destroyMethod` 反射调用；清空缓存，幂等。

**一个 Bean 的完整创建（`createBean`，生命周期心脏）：**

```text
① creating.add(type) ── 已存在 → 抛"循环依赖"
② instantiate()       ── 三分支（见下）
③ invokeInit()        ── 实现了 InitializingBean 则调 afterPropertiesSet()
④ 遍历 postProcessors ── ToolRegistry / HookRegistry 在这里收集 @Tool/@Hook（第二波扫描）
⑤ 单例 → singletons.put + creationOrder.add；prototype → 直接返回不缓存
⑥ finally 恒 remove(type)，防止一次失败让该类型永久被误判为循环依赖
```

**`instantiate` 三分支（实例化方式）：**

```text
A) 工厂方法  → instantiateFromFactory：getBean(配置类) → 按方法形参解析依赖 → method.invoke
B) 有注入构造器 → resolveDependencies(构造器形参, overrides) → newInstance
C) 无参构造 + 字段注入 → newInstance(无参构造) → injectFields(definition, instance)
```

**依赖解析 `resolveDependencies`（三段式，构造器与工厂形参通用）：**

```text
① 按位置覆盖：overrides[i] 类型匹配 parameters[i] 就直接用
② 剩余 overrides 按类型匹配，填给未解析参数
③ 容器兜底：形参带 @Named 按名字 getBean(v)；否则 getBean(参数类型)（递归，可触发嵌套创建与循环检测）
```

字段注入 `injectFields` 同理：`@Named` 字段按名字、其余按字段类型。

### 3.4 `AnnotationScanner`：类路径扫描（`core/scan/AnnotationScanner.java`）

负责"发现"，纯只读，不持有容器状态。

`scanBeanDefinitions(basePackage)` = `scanComponents(basePackage)` 逐个 `BeanDefinition.from`。

`scanComponents` 流程：
1. 包名转目录相对路径：`classLoader.getResources("com/learn/mycc/core".replace('.','/'))`，拿到**多个 URL**（可能多个 classpath/jar）；
2. 按协议分流 `collect(url)`：`file` 走目录递归 `scanDirectory`；`jar` 走 jar 条目遍历 `scanJar`；
3. `scanDirectory` 递归子目录，`.class` 文件拼全限定名后 `loadClass`；
4. `scanJar` 只取 `basePackage` 前缀下以 `.class` 结尾的条目；
5. `loadClass` 用 `Class.forName(className, false, classLoader)` —— **`initialize=false` 只加载不触发静态初始化**，避免扫描阶段副作用；
6. 统一过滤：`@Component` **或** `@Configuration`。

> **关键坑：`isAnnotationPresent` 不穿越元注解**。`@Configuration` 虽然以 `@Component` 为元注解，但反射在"该类身上"直接查不到 `@Component`，所以过滤条件必须显式 `|| c.isAnnotationPresent(Configuration.class)`（`AnnotationScanner.java` 过滤处）。

### 3.5 `ToolRegistry` / `HookRegistry`：BeanPostProcessor（`core/tool`、`core/hook`）

两者是**同构模式**：都实现 `BeanPostProcessor`，在 bean 创建完成后（`createBean` 第④步）被容器调用，反射扫描该实例的方法并注册元数据。设计叫"**被动捕获**"：只要把它们 add 为后置处理器，任何组件后续创建时 `@Tool`/`@Hook` 都会被自动收集，与 IoC 容器天然解耦——不需要再扫 classpath。

- `ToolRegistry.postProcessAfterInitialization`：遍历 bean 声明方法，有 `@Tool` 就 `register(new ToolDefinition(...))`。`toolsByName` 用 LinkedHashMap 保持注册顺序；**重名 `putIfAbsent` 直接抛错**。
- `HookRegistry`：同理，按 `@Hook.event()` 分组存到 `EnumMap<HookEventType, List<HookDefinition>>`；`register` 时会**校验方法签名必须为单个 `HookEvent` 参数**（启动期快速失败）。

**这两者为什么被 "registerSingleton"**：它们既是 BPP，又要能被 `@Inject` 注入到业务 Bean（如 `HookDispatcher` 注入 `HookRegistry`）。所以 `DefaultIocContainer` 构造里做了三件事（见 3.6）。

### 3.6 `IocContainer` / `DefaultIocContainer`：门面（`core/context/IocContainer.java`）

接口定义 6 类 API：注册（3 个 register 重载 + registerSingleton）、启动（start）、取 Bean（getBean(type) / getBean(type,args) / getBean(name) / getBeansOfType）、后置处理器（addBeanPostProcessor）、注册表访问（getToolRegistry / getHookRegistry）、关闭（close）。另有静态工厂 `IocContainer.create()`。

`DefaultIocContainer`（同文件、包私有）：
- 字段：`BeanFactory`、`AnnotationScanner`、`ToolRegistry`、`HookRegistry`。
- **构造函数做的事**：
  1. `beanFactory.addBeanPostProcessor(toolRegistry)`、`addBeanPostProcessor(hookRegistry)` —— 让工具的捕获随每个 bean 生效；
  2. `registerSingleton(ToolRegistry.class, toolRegistry)` / `(HookRegistry.class, hookRegistry)` / `(IocContainer.class, this)` —— 三件套按类型可注入。
- **注册的收敛入口 `registerAll`**：三个 `register` 重载全部汇到这里，先 `expandFactoryMethods` 再统一 `beanFactory.register(...)`，保证展开逻辑唯一。
- **`expandFactoryMethods`（工厂展开）**：遍历定义，凡标注 `@Configuration` 的，把它的每个 `@Bean` 方法额外构建一个 `BeanDefinition.fromFactoryMethod`（名称取 `@Bean.name` 或方法名，作用域取 `@Scope` 或 SINGLETON）。所以**一个配置类注册后实际生成 N+1 个定义**：配置类自身 1 个 + 每个 `@Bean` 方法 1 个。

### 3.7 外围组件（了解即可）

- **hook 派发**：`HookDispatcher` 把事件按注册顺序同步派发给订阅者；单个钩子抛异常只记告警、不中断其它；可否决事件任一段返回 `HookDecision.deny(...)` 即整体短路。
- **permission 机制**：`PermissionPolicy.decide(store, toolName, risk)` 纯函数——规则命中（会话>项目）优先于风险默认；无规则时 HIGH→`ASK`、LOW→`ALLOW`。`UserConfirmation` 是人工审批端口接口；`UnavailableUserConfirmation` 是 fail-closed 兜底（无 UI 时恒返回 `UNAVAILABLE`，调用方降级为拒绝）。
- **tool Schema**：`ParameterSchemaGenerator` 把方法参数反射成 JSON Schema（`{"type":"object","properties":…,"required":[…]}`），仅支持基础类型/枚举。
- **exception**：`MyccException extends RuntimeException`，容器装配/扫描/工具调用统一抛它。
- **config**：`ApplicationConfig`（`@Component`）携带工作区路径。

---

## 4. 关键流程梳理

下面把第 3 章串成一条端到端调用链。先给全貌时序图，再逐条展开。

```text
 app / 测试代码                           DefaultIocContainer                  BeanFactory               AnnotationScanner / BPP
 ─────────────                           ───────────────────                  ────────────               ──────────────────────
 ① IocContainer.create()  ─────────────►  new DefaultIocContainer()
                                          ├─ new BeanFactory()
                                          ├─ new AnnotationScanner(...)
                                          └─ ctor：add BPP×2 + registerSingleton×3
                                                                                     (容器就绪)
 ② register("com.learn.mycc") ─────────►  registerAll(scanner.scanBeanDefinitions(...))
                                          ├─ scanComponents → 过滤 @Component/@Configuration
                                          ├─ BeanDefinition.from(...) 每个类一张蓝图
                                          └─ expandFactoryMethods 展开 @Configuration.@Bean
                                                                        │
                                                                        ▼
                                                            beanFactory.register(defs...)
                                                            ├─ 类型表 / 名字表双防重
                                                            └─ {一份蓝图入表}
 ③ start()                  ─────────►  beanFactory.preInstantiateSingletons()
                                           对每个 SINGLETON 定义调用 getBean(type) ──► createBean(definition)
                                              ├─ creating.add(type)（循环依赖检查）
                                              ├─ instantiate
                                              │    ├─ 工厂方法 → getBean(配置类) → 反射 invoke(@Bean方法)
                                              │    ├─ 构造器注入 → resolveDependencies(构造形参) → newInstance
                                              │    └─ 无参 + 字段注入  → injectFields
                                              ├─ invokeInit（InitializingBean）
                                              ├─ BPP：ToolRegistry 扫 @Tool、HookRegistry 扫 @Hook  （第二波扫描）
                                              ├─ 单例 → singletons.put + creationOrder.add
                                              └─ finally creating.remove(type)
 ④ getBean(X.class)         ─────────►  beanFactory.getBean(X.class)
                                          ├─ singletons 命中？→ 返回
                                          ├─ definitionsByType 命中？→ createBean
                                          └─ resolveAssignable 接口可匹配回退（唯一实现）
 ⑤ close()                  ─────────►  beanFactory.close()
                                          按 creationOrder 逆序：
                                          ├─ DisposableBean.destroy()
                                          └─ @Bean destroyMethod 反射调用
                                          清空缓存
```

### A. 容器创建与装配（`create()` + ctor）

`IocContainer.create()` → `new DefaultIocContainer()`。构造函数把 `BeanFactory`、`AnnotationScanner`、`ToolRegistry`、`HookRegistry` 组装好，并把两个注册表挂为 BPP、把注册表和容器自身注入为单例。**这一步之后，容器就可以"收单"了。**

### B. 类路径扫描注册（`register(String)`）

调 `register("com.learn.mycc")` → 扫描器把包下所有 `@Component`/`@Configuration` 类变成 `BeanDefinition` → `registerAll` 展开 `@Bean` 工厂方法 → `beanFactory.register` 双表入册。**注册阶段只产生"蓝图"，不产生实例。**

手动注册另外两个等价入口：`register(Class...)`（逐个 `BeanDefinition.from`）、`register(BeanDefinition...)`（完全手动）。三条路径殊途同归于 `registerAll`。

### C. 启动预创建（`start()` → preInstantiateSingletons）

按 `definitionsByType` 注册顺序，把每个 SINGLETON 定义 `getBean` 一遍。依赖会让 `getBean` 递归触发嵌套创建，因此**实际创建顺序 = 被依赖方先、依赖方后**——这也是 close 逆序销毁语义正确的根本保证。prototype 定义跳过，留到被 `getBean` 时按需创建。

### D. 单个 Bean 创建生命周期（`createBean`）

见 3.3 的六步图。重点再强调**两波扫描**的区分：

- **第一波（注册/发现）**：classpath 扫描 `@Component`，产出蓝图；
- **第二波（创建/捕获）**：每个 Bean 实例化完成后，`ToolRegistry`/`HookRegistry` 对其反射扫 `@Tool`/`@Hook`。

所以 `@Tool`/`@Hook` 所在的类必须先成为 Bean（`@Component` 或被 `@Configuration/@Bean` 产出），它的工具/钩子才会被发现。

### E. 构造器/字段注入过程（三段式 + 回退）

`resolveDependencies`：位置覆盖 → 类型匹配 → 容器兜底（`@Named` 按名 / 否则按类型）。字段注入路径：无参构造后逐字段 `setAccessible(true)` + `field.set`。注入本身是递归的：注入一个依赖可能触发它的完整创建。

### F. `@Configuration`/`@Bean` 展开与调用

注册时 `expandFactoryMethods` 为每个 `@Bean` 方法生成独立蓝图（注册键=返回类型）；创建该 Bean 时 `instantiateFromFactory` 先 `getBean` 配置类单例，再按方法形参解析依赖、`method.invoke` 产出实例，产物走**标准生命周期**（init + BPP + 缓存/prototype 跳过 + close 销毁）。

### G. `getBean` 接口回退

`getBean(XInterface.class)`：精确命中者优先，否则 `resolveAssignable` 在单例（`isInstance`）+ 定义（`isAssignableFrom` 惰性创建）中收集候选。**恰好一个**返回；多个抛"多个可匹配"；没有抛"未注册"。要区分多实现只能在注入点用 `@Named` 指定名字。

### H. 关闭销毁（`close()`）

按创建顺序逆序：`DisposableBean.destroy()` → 工厂 Bean 再调 `destroyMethod`（如 JLine `Terminal.close`）。随后清空缓存，幂等可重复调用。

> **一句话总结**：注册期扫 classpath → 展开工厂方法 → 入双表；启动期按序创建单例（注入 + 初始化回调 + BPP 捕获工具/钩子）；使用期 `getBean` 缓存或现造；关闭期逆序销毁。

---

## 5. 阅读路线建议

按"由浅入深、由叶到根"推进，每步标注"读什么、为什么、看完接哪"。

### 第一层：注解（先建立词汇表）· 约 20 分钟

依次读 `core/annotation/` 下：`Component` → `Inject` → `Named` → `Scope`+`ScopeType` → `Tool`+`ToolRisk` → `Configuration` → `Bean` → `Hook` → `ToolParam`。

> 只要扫一眼每个注解的 `@Target` 和 doc；重点记住 `@Configuration` 以 `@Component` 为元注解、`@Tool`/`@Hook` 是方法级。

### 第二层：`BeanDefinition`（蓝图）· 约 20 分钟

读 `core/bean/BeanDefinition.java`，配合 `core/exception/MyccException.java`（看到哪抛哪，先认识统一异常）。**位置**：name/scope/注入方式的默认推导、`resolveInjectionConstructor` 四条规则、`resolveInjectFields` 上溯父类。看懂这张"蓝图"再进内核。

### 第三层：`BeanFactory`（内核，重点，约 1 小时）

读 `core/bean/BeanFactory.java`，这是全模块最难也最核心的类。拿 3.3 的数据结构表当地图，逐方法过：`register` → `getBean(Class)` → `createBean` → `instantiate` 三分支 → `resolveDependencies` → `preInstantiateSingletons` → `close`。中途遇到 `InitializingBean`/`DisposableBean`/`BeanPostProcessor` 三个接口，各 5 分钟扫一眼即可。**看完这一层，IoC 的 80% 就被你看完了。**

### 第四层：`IocContainer`（门面，约 20 分钟）

读 `core/context/IocContainer.java`。重点看 `DefaultIocContainer` 构造函数（BPP 挂接 + registerSingleton 三件套）和 `expandFactoryMethods`（工厂展开）。此时你会发现第三层的逻辑"被谁调用"终于闭环。

### 第五层：`AnnotationScanner`（发现，约 20 分钟）

读 `core/scan/AnnotationScanner.java`。重点：包→URL 的定位、file/jar 协议分流、`Class.forName(..., false, ...)` 延迟加载、过滤条件为何要显式匹配 `@Configuration`。

### 第六层：BPP 扩展（工具/钩子，约 30 分钟）

读 `core/tool/ToolRegistry.java` + `core/tool/ToolDefinition.java` + `core/hook/HookRegistry.java` + `core/hook/HookDefinition.java`。这类"被动捕获"模式日后写自己的扩展会反复用到。再用 10 分钟看 `core/tool/ParameterSchemaGenerator.java` 了解 Schema 生成。

### 第七层：外围（permission / hook 派发，30 分钟）

读 `core/permission/PermissionPolicy.java` + `PermissionRuleStore.java` + `UserConfirmation.java`；`core/hook/HookDispatcher.java` + `HookEventType.java` + `HookDecision.java`。理解"core 只定义机制，实现由上层注入"是模块边界纪律的实例。

### 收尾：用测试反向验证

读测试：`core/bean/BeanFactoryTest.java`、`core/context/IocContainerTest.java`，重点看 `bean/fixture/FactoryConfig.java`、`RecordingBpp`（BPP 记录器）、`ProtoBean`（prototype 样例）、`NamedConsumer`（按名注入）。**代码读不懂时，跑/看对应的测试是理解行为的捷径。**

---

## 6. 运行验证与扩展指南

### 6.1 构建与测试

```bash
mvn -pl mycc-core test            # 仅跑 mycc-core 模块测试
mvn -pl mycc-core -am test        # 连同依赖模块
mvn clean install                 # 全量构建 + 测试
```

对应测试文件：`mycc-core/src/test/java/com/learn/mycc/core/` 下的 `bean/BeanFactoryTest`、`context/IocContainerTest` 及 `bean/fixture/`、`context/fixture/` 夹具。改动 IoC 后先跑这两类，阶段验收=全绿。

> 注意本仓库当前正处于 **Spring 化 IoC 重构**（见设计文档 `docs/superpowers/specs/2026-09-04-spring-ioc-refactor-design.md`）进行中，新增注解/能力会对齐该文档的 S0 清单；改代码前先读它。

### 6.2 如何基于现有机制扩展

| 想做的事 | 怎么做 |
| --- | --- |
| 新增一个受管组件 | 写一个 `@Component` 类，字段/构造器用 `@Inject`，交给 `register("com.learn.mycc")` 扫描即可 |
| 补充一个组装规则（不改变类的构造签名） | 写 `@Configuration` 类 + `@Bean` 方法，返回类型作为注册键 |
| 造一个每次取用都新建的会话级对象 | 类标 `@Scope(PROTOTYPE)`，或用 `@Bean @Scope(PROTOTYPE)` + `getBean(type, args)` 绑定调用侧入参 |
| 让某接口的多实现按名区分 | 注入点 `@Named("beanName")` 显式指定 |
| 给容器加一类全局观察点 | 实现 `BeanPostProcessor`，`addBeanPostProcessor(...)` 挂进去；参照 `ToolRegistry` |
| 新增一种 agent 能力 | 在任意 `@Component` bean 里写 `@Tool` 方法（**必须单例**，见踩坑 6.3） |
| 订阅生命周期事件 | 写 `@Hook(event = HookEventType.XXX)` 方法，签名 `(HookEvent)` |
| 让 core 提供某种跨模块机制 | 仿 `UserConfirmation`：core 只放接口 + 纯机制（如 `PermissionPolicy`），实现放上层模块靠接口可匹配注入 |

### 6.3 容易踩的坑（务必记住）

1. **`isAnnotationPresent` 不穿越元注解**：`@Configuration` 不是"直接有着" `@Component`，扫描器必须显式 `|| isAnnotationPresent(Configuration.class)`。自己写"元注解"想当然会漏。
2. **`@Tool` 必须放在单例 bean 上**：prototype bean 每次创建都会触发 BPP，`ToolRegistry` 的 `putIfAbsent` 会对同一工具名重复注册直接抛错。
3. **接口多实现扫雷**：`getBean(接口)` 存在多个实现会抛"多个可匹配"；解析不了抛"未注册"。真要多实现就 `@Named`。
4. **多个 `@Inject` 构造器 = 不明确**：一个类最多只能有一个 `@Inject` 构造器。
5. **`getBean(type, args)` 只对 prototype 生效**：单例已创建则完全忽略 args。
6. **循环依赖只检测、不解决**：`creating` 集合检测到即在创建中重入就抛错，不是 Spring 的三级缓存。
7. **`registerSingleton` 与 `register` 冲突**：先用 `register` 注册过的类型不能再 `registerSingleton`（会抛重复注册）。
8. **prototype 不入缓存、不记创建顺序**：依赖 prototype 注入不会"等待"它参与预创建；要注入预期还是老老实实单例。
9. **扫描是"只加载不初始化"**：`Class.forName(name, false, loader)`，避免扫描触发静态块副作用；千万别手痒改成 true。
10. **JDK 模块化下私有成员**：反射写私有字段/调私有方法必须先 `setAccessible(true)`（工厂代码已处理；新增路径记得带上）。
11. **参数名依赖编译选项**：`ParameterSchemaGenerator` 取参数名需 `-parameters` 编译，否则是 `arg0` 这类占位名。
12. **重复注册直接抛异常**：同类型或同名（`@Named`/`@Bean name` 重叠）进 `definitionsByType`/`definitionsByName` 都会抛"重复注册"，启动期快速失败是特性不是 bug。

### 6.4 调试建议

- **第一断点**：`BeanFactory.createBean`（`BeanFactory.java` 的 `createBean`）——每个 bean 创建都会经过它，Step Over 看 instantiate/init/BPP/缓存。
- **看依赖解析**：`resolveDependencies` 的第三步（容器兜底），能看清"这个参数到底按名还是按类型取的"。
- **看第二波扫描**：`ToolRegistry.postProcessAfterInitialization` / `HookRegistry.postProcessAfterInitialization`。
- **看循环依赖**：`createBean` 的 `creating.add` 那句，是检测到"创建中重入"抛错的现场。
- **读测试最快**：改行为前先看 `BeanFactoryTest`、`IocContainerTest` 的用例意图和断言，跑绿再动手。

---

## 附：与相邻文档的关系

| 文档 | 与本指南的关系 |
| --- | --- |
| `docs/superpowers/specs/2026-08-27-mycc-prd.md` | 需求来源，v2 功能点的"为什么"都在这里 |
| `docs/superpowers/specs/2026-09-04-spring-ioc-refactor-design.md` | 本模块正在进行的能力补齐（registerSingleton/接口可匹配/@Configuration·@Bean/prototype/按名查找）设计蓝本 |
| `docs/design/design-standards.md` | 模块边界、扩展点、层与层如何衔接的总规范 |
| `docs/standards/coding-standards.md` | Java 风格、注释与提交纪律（Conventional Commits） |