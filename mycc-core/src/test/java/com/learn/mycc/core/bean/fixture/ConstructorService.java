package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Component;

@Component
public class ConstructorService {

    private final ConstructorDep dep;

    public ConstructorService(ConstructorDep dep) {
        this.dep = dep;
    }

    public ConstructorDep getDep() {
        return dep;
    }
}
