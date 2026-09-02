package com.learn.mycc.tools;

/**
 * bash 工具执行结果：封装一次 shell 命令的标准输出、标准错误与退出码。
 *
 * <p>设计思路：以不可变 record 承载命令执行的结果，便于在工具系统与调用方之间
 * 零副作用地传递；退出码与 stdout/stderr 分开保存，调用方可据此判断执行是否
 * 成功以及失败的原因。</p>
 *
 * @param stdout   标准输出（stdout）的完整文本。可能为空串，表示进程未向 stdout 写入内容
 * @param stderr   标准错误（stderr）的完整文本。可能为空串，表示进程未向 stderr 写入内容
 * @param exitCode 进程退出码；0 通常表示成功，非 0 表示执行失败或被信号终止
 */
public record BashResult(String stdout, String stderr, int exitCode) {
}
