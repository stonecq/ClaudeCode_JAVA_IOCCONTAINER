package com.learn.mycc.core.exception;

/**
 * 框架顶层运行时异常，携带可读的中文错误信息。
 * 设计为 RuntimeException（非受检）：容器装配/扫描/工具调用等失败统一抛此异常，
 * 调用方按需捕获即可，无需逐层声明；同时避免受检异常污染框架接口签名。
 */
public class MyccException extends RuntimeException {

    /**
     * @param message 可读的错误描述，不允许为 null
     */
    public MyccException(String message) {
        super(message);
    }

    /**
     * @param message 可读的错误描述，不允许为 null
     * @param cause   底层根因（多为反射或 IO 异常），可 null
     */
    public MyccException(String message, Throwable cause) {
        super(message, cause);
    }
}
