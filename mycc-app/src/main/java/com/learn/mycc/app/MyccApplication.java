package com.learn.mycc.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.tool.ParameterSchemaGenerator;
import com.learn.mycc.core.tool.ToolDefinition;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.tools.BashTool;
import com.learn.mycc.tools.FileTools;
import com.learn.mycc.tools.SearchTools;

/** v1 演示启动器（M6 完善为完整 REPL）：装配容器、列出全部工具与 JSON Schema。 */
public final class MyccApplication {

    private MyccApplication() {
    }

    public static void main(String[] args) {
        IocContainer container = assemble();
        try {
            printTools(container.getToolRegistry());
        } finally {
            container.close();
        }
    }

    static IocContainer assemble() {
        IocContainer container = IocContainer.create();
        container.register(FileTools.class, BashTool.class, SearchTools.class);
        container.start();
        return container;
    }

    private static void printTools(ToolRegistry registry) {
        ObjectMapper mapper = new ObjectMapper();
        ParameterSchemaGenerator schemaGenerator = new ParameterSchemaGenerator();
        System.out.println("已注册 " + registry.getAll().size() + " 个工具:");
        for (ToolDefinition tool : registry.getAll()) {
            JsonNode schema = mapper.valueToTree(schemaGenerator.generate(tool.getMethod()));
            System.out.println("- " + tool.getName() + ": " + tool.getDescription());
            System.out.println("  schema: " + schema.toPrettyString().indent(2));
        }
    }
}
