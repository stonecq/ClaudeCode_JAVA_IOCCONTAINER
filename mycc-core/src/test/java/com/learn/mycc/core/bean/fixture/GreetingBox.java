package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Scope;
import com.learn.mycc.core.annotation.ScopeType;

/** getBean(Class, Object... args) 按位置覆盖构造参数的测试夹具。 */
@Scope(ScopeType.PROTOTYPE)
public class GreetingBox {

    private final String message;

    public GreetingBox(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}