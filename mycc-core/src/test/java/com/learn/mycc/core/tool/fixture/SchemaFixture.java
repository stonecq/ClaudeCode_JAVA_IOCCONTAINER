package com.learn.mycc.core.tool.fixture;

import com.learn.mycc.core.annotation.ToolParam;

/** ParameterSchemaGenerator 测试夹具：覆盖基本类型 / 枚举 / @ToolParam 元数据。 */
public final class SchemaFixture {

    public enum Mode { FAST, SAFE }

    public String mixed(@ToolParam(description = "路径") String path,
                        int count,
                        @ToolParam(required = false) boolean verbose,
                        Mode mode) {
        return null;
    }
}
