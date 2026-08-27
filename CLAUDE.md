# CLAUDE.md — mycc 项目工作指引

## 项目简介

从零实现简易版 Claude Code（学习型 agent 工具）。核心：自研迷你 IoC 容器、可插拔 LLM、`@Tool` 工具系统、agent 循环、多界面适配（CLI 先行，Web 二期）。
- 根包：`com.learn.mycc`
- JDK 17，Maven 多模块，测试 JUnit5 + AssertJ

## 标准文档（工作前必读）

| 用途 | 路径 |
| --- | --- |
| 需求文档（PRD） | `docs/superpowers/specs/2026-08-27-mycc-prd.md` |
| 技术选型 | `docs/tech/tech-stack.md` |
| 设计规范 | `docs/design/design-standards.md` |
| 编码规范 | `docs/standards/coding-standards.md` |
| v1 开发计划 | `docs/plans/2026-08-27-v1-development-plan.md` |
| 开发日志 | `dev-log/`（每工作日一个 `YYYY-MM-DD.md`） |

## 工作说明（硬性纪律）

1. **增量推进**：严格按开发计划分阶段（M1→M6）执行。一个阶段完成、测试通过后再进入下一阶段；不提前实现未排期功能（YAGNI）。
2. **开发日志纪律**：每次工作会话结束前，更新 `dev-log/<当日>.md` 的「今日完成 / 待办事项 / 阻塞与风险 / 明日计划」。新的一天若该文件不存在，先复制 `dev-log/TEMPLATE.md`。
3. **测试优先**：写实现前先写失败测试（JUnit5 + AssertJ）；阶段验收 = 该阶段测试全绿。
4. **完成判定**：声称"完成/通过"前必须实际运行构建与测试验证（verification-before-completion），先给证据再下结论。
5. **架构纪律**：依赖单向、无环；`mycc-core` 为纯机制层；接口与实现分离；新增能力走扩展点（`@Tool` / SPI / `InteractionPort`）。
6. **调试纪律**：遇到 bug/测试失败，先走 systematic-debugging 定位根因，不靠猜测改代码。
7. **提交纪律**：Conventional Commits（`feat/fix/refactor/test/docs/chore`），粒度小；**仅在用户要求时提交**。
8. **文件路径**：所有文件操作使用完整 Windows 绝对路径（如 `V:\learn\work_space\learn\learn-my-cc\...`）。
9. **安全**：API key 等敏感信息禁入日志与代码库；命令/路径参数做基础校验。

## 构建命令

```bash
mvn clean install                 # 全量构建 + 测试
mvn -pl mycc-core test            # 单模块测试
mvn -pl mycc-agent -am test       # 连带依赖模块测试
java -jar mycc-app/target/mycc-app.jar   # 运行（fat-jar，M6 后可用）
```

## 模块地图（可插拔）

```
mycc-core(机制层: IoC/注解/ToolRegistry) · mycc-ui(仅 UI 接口: InteractionPort/OutputEvent)
  └─ mycc-ai(LLM) · mycc-tools(内置工具) · mycc-storage(存储)   ← 依赖 core
        └─ mycc-agent(agent 循环，只依赖接口，含 mycc-ui)
              ├─ mycc-cli(CLI 渲染，实现 mycc-ui) · mycc-web(v2) ← 具体 UI 实现
              └─ mycc-app(启动器: 选择并绑定某个 UI 模块)
```
