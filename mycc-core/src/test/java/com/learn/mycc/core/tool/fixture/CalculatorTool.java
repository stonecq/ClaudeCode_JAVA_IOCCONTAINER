package com.learn.mycc.core.tool.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Tool;
import com.learn.mycc.core.annotation.ToolParam;

@Component
public class CalculatorTool {

    @Tool(name = "add", description = "两个数相加")
    public int add(@ToolParam(description = "被加数") int a, @ToolParam(description = "加数") int b) {
        return a + b;
    }

    @Tool(name = "subtract", description = "两个数相减")
    public int subtract(int a, int b) {
        return a - b;
    }
}
