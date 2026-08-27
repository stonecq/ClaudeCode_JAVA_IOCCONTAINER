package com.learn.mycc.core.scan.fixture;

import com.learn.mycc.core.annotation.Component;

@Component
public class Greeter {

    private final SupportService support;

    public Greeter(SupportService support) {
        this.support = support;
    }

    public String greet(String name) {
        return support.format(name);
    }
}
