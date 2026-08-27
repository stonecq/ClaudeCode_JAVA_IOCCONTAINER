package com.learn.mycc.core.exception;

/** 框架顶层运行时异常，携带可读的错误信息。 */
public class MyccException extends RuntimeException {

    public MyccException(String message) {
        super(message);
    }

    public MyccException(String message, Throwable cause) {
        super(message, cause);
    }
}
