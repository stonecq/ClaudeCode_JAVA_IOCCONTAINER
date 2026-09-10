package com.learn.mycc.core.tool.fixture;

import com.learn.mycc.core.annotation.ToolParam;
import com.learn.mycc.core.tool.ToolContext;

/** ParameterSchemaGenerator 测试夹具：覆盖基本类型 / 枚举 / @ToolParam 元数据 / 工具上下文。 */
public final class SchemaFixture {

    public enum Mode { FAST, SAFE }

    public String mixed(@ToolParam(description = "路径") String path,
                        int count,
                        @ToolParam(required = false) boolean verbose,
                        Mode mode) {
        return null;
    }

    public String contextual(ToolContext ctx, @ToolParam(description = "文本") String text) {
        return null;
    }
}
