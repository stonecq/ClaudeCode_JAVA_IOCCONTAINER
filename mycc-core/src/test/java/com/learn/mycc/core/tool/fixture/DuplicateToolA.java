package com.learn.mycc.core.tool.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Tool;

@Component
public class DuplicateToolA {

    @Tool(name = "same", description = "工具 A")
    public String run() {
        return "A";
    }
}
