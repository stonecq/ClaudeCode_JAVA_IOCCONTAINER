package com.learn.mycc.tools;

/** bash 工具执行结果：标准输出、错误输出与退出码。 */
public record BashResult(String stdout, String stderr, int exitCode) {
}
