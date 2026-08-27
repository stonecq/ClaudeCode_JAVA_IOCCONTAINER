package com.learn.mycc.core.context.fixture;

import com.learn.mycc.core.annotation.Component;

@Component
public class GreeterImplB implements Greeter {

    @Override
    public String greet() {
        return "B";
    }
}
