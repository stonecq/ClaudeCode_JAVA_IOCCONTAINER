package com.learn.mycc.core.bean.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Inject;

@Component
public class FieldInjectedService {

    @Inject
    private FieldDep fieldDep;

    public FieldDep getFieldDep() {
        return fieldDep;
    }
}
