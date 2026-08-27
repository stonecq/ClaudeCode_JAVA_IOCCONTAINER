# Java 编码规范

实现所有模块时遵循以下规范。

## 语言级别

- JDK 17。可优先使用 record、sealed、switch 模式匹配、`List.of` 等，但以可读性为先。
- 不开 `--enable-preview`。

## 代码风格

- 4 空格缩进；单行不超过 120 字符。
- 类名 PascalCase，方法/变量 camelCase，常量 `UPPER_SNAKE`。
- 不会被重新赋值的局部变量/参数一律 `final`。
- 集合优先不可变（`List.of` / `Collections.unmodifiable*`）。

## 接口与实现

- 面向接口编程；内部可确定的地方用具体实现。
- DTO / 消息模型用 record 或不可变类，避免可变 getter/setter 堆积。

## 注释

- **默认不写注释**；仅在"为什么"非显然时写一行注释（约束、陷阱、变通、针对某 bug 的 workaround）。
- 公共 API 用一句话 Javadoc 说明用途。
- 不写描述代码行为的注释（好命名即文档）。

## 测试

- JUnit 5 + AssertJ；测试类命名 `XxxTest`。
- 单元测试不访问真实网络/系统文件（用临时目录、Mock WebServer）。
- TDD：先写失败测试，再实现，再确认通过。

## 提交规范

- Conventional Commits：`feat` / `fix` / `refactor` / `test` / `docs` / `chore`。
- 提交粒度小：一个逻辑改动一个提交。
- 仅在用户要求时提交。

## 禁止事项

- 不用 `System.out` 打印（用 SLF4J）。
- 不留死代码 / 注释掉的代码块。
- 不提交 secrets、本地配置（`*.local.*` 已 gitignore）。
- 不写未排期的功能（严格按开发计划推进）。
