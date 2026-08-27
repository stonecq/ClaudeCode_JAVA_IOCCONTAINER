package com.learn.mycc.core.scan.fixture;

import com.learn.mycc.core.annotation.Component;

@Component
public class SupportService {

    public String format(String name) {
        return "hi " + name;
    }
}
