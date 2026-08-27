package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Component;

@Component
public class MissingDepConsumer {

    private final NotABean dep;

    public MissingDepConsumer(NotABean dep) {
        this.dep = dep;
    }
}
