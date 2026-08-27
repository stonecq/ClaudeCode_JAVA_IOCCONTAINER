package com.learn.mycc.core.tool.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Tool;

@Component
public class DuplicateToolB {

    @Tool(name = "same", description = "工具 B")
    public String run() {
        return "B";
    }
}
