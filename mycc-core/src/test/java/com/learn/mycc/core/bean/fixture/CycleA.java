package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Component;

@Component
public class CycleA {

    private final CycleB b;

    public CycleA(CycleB b) {
        this.b = b;
    }
}
