package com.learn.mycc.core.scan.fixture;

/** 无 @Component 注解，扫描时应被过滤。 */
public final class PlainUtil {

    private PlainUtil() {
    }

    public static String noop() {
        return "";
    }
}
