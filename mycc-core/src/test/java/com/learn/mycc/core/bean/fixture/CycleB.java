package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Component;

@Component
public class CycleB {

    private final CycleA a;

    public CycleB(CycleA a) {
        this.a = a;
    }
}
