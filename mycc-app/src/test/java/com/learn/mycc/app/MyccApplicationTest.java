package com.learn.mycc.app;

import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyccApplicationTest {

    @Test
    void assemblesAllSevenBuiltinTools() {
        MyccApplication application = new MyccApplication();
        IocContainer container = application.getIocContainer();
        try {
            ToolRegistry registry = container.getToolRegistry();
            List<String> names = registry.getAll().stream().map(tool -> tool.getName()).toList();
            assertThat(names).containsExactlyInAnyOrder(
                    "read_file", "write_file", "edit_file", "bash", "glob", "grep", "search_files");
        } finally {
            container.close();
        }
    }
}
