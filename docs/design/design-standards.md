# 设计规范

> 架构总览见 PRD 第 4 节。本文为各模块实现时的设计约定，实现前必读。

## 模块边界与依赖

- 依赖**单向、无环**（见 PRD 4.2），禁止反向依赖与模块间循环。
- 接口（SPI/API）定义在消费方模块或 `mycc-core`；具体实现放实现方模块，**通过 IoC 容器装配**，实现类不对外直接 new。
- `mycc-core` 是纯机制层，**不依赖任何业务模块**。

## 包结构约定

```
com.learn.mycc.<模块名>/
├── annotation/   # 注解
├── api/ 或 spi/  # 接口
├── core/         # 核心机制实现
├── support/      # 辅助实现
└── model/        # 数据模型
```

## 扩展点（新增能力不改核心）

| 想新增… | 做法 |
| --- | --- |
| LLM 提供方 | 实现 `LlmProvider` 接口 + 配置中选择 |
| 工具 | 写一个类 + `@Tool` 注解，容器自动注册 |
| 界面 | 实现 `InteractionPort` + 注册 |
| 存储后端 | 实现 `Storage` SPI |

## 命名约定

- 接口：`XxxProvider` / `XxxRegistry` / `XxxSpi` / `XxxService`。
- 实现类：用语义名（`FileStorage`、`CliPort`），避免 `XxxImpl` 泛化命名。
- 注解：`@Xxx`（如 `@Tool`、`@Hook`）。
- 数据模型：不可变 record 优先。

## 异常体系

- 顶层 `MyccException`（含错误码），业务异常继承之。
- agent/工具层异常：工具失败**回填给 LLM**（不崩会话）；框架性错误上抛给调用方。
- 日志级别明确；**敏感信息（API key、路径）禁入日志**。

## 配置加载优先级

系统属性/环境变量 > 用户配置 `~/.mycc/config` > 内置默认值。

## 流式输出约定

- agent 只通过 `InteractionPort` 下发 `OutputEvent`，不直接碰终端/网络。
- `OutputEvent` 是 v1 CLI 与 v2 SSE 的公共协议（type/payload/sessionId/seq）。
