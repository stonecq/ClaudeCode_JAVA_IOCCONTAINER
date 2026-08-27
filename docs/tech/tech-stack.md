# 技术选型

> 总览见 PRD `docs/superpowers/specs/2026-08-27-mycc-prd.md`。本文为实现期的具体依赖与命令参考。

## 基础

| 项 | 选型 | 说明 |
| --- | --- | --- |
| JDK | Java 17 (LTS) | 用稳定特性（record、sealed 等），不开 preview |
| 构建 | Maven 多模块 | 父 pom 管理依赖版本与插件 |
| 根包 | `com.learn.mycc` | |
| 日志 | SLF4J 2.x + Logback 1.x | |

## 依赖（模块 → 依赖）

| 模块 | 依赖 | 用途 |
| --- | --- | --- |
| mycc-core | 纯 JDK | 自研 IoC 不依赖第三方框架 |
| mycc-ui | mycc-core（必要时复用模型类型） | 仅 UI 接口：InteractionPort / OutputEvent |
| mycc-ai | `java.net.http.HttpClient`（JDK） | 调 OpenAI 兼容 HTTP |
| | `com.fasterxml.jackson.core:jackson-databind` | JSON 序列化/工具 Schema |
| mycc-tools | mycc-core | 工具注解与类型 |
| mycc-storage | `jackson-databind` | 会话 JSON 落盘 |
| mycc-cli | `org.jline:jline` | 富交互（补全/历史/多行/ANSI） |
| | `info.picocli:picocli` | 命令与参数解析 |
| mycc-web（v2） | `org.eclipse.jetty:jetty-server` | 嵌入式服务器 + SSE |
| 测试 | `org.junit.jupiter:junit-jupiter` + `org.assertj:assertj-core` | |
| 打包 | `maven-shade-plugin` | 单 fat-jar |

> 依赖方向：mycc-cli / mycc-web → mycc-ui（实现）+ mycc-agent（调用）；mycc-agent → mycc-ui（接口）+ core/ai/tools/storage；mycc-app 负责选择并绑定某个 UI 模块。

> 版本号以 Maven 中央仓库当前稳定版为准，父 pom 统一用 `dependencyManagement` 锁定。

## 常用命令

```bash
# 全量构建 + 测试
mvn clean install

# 单模块测试
mvn -pl mycc-core test

# 指定依赖构建（用于只测某个模块时）
mvn -pl mycc-agent -am test

# 运行 fat-jar
java -jar mycc-app/target/mycc-app.jar
```

## 目录约定（每个模块内）

```
src/main/java/com/learn/mycc/<模块名>/
├── annotation/   # 注解
├── api/ 或 spi/  # 接口
├── core/         # 核心机制实现
├── support/      # 辅助实现
└── model/        # 数据模型
src/test/java/    # 单元测试（JUnit5 + AssertJ）
```
