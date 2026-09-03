package com.learn.mycc.tools;

import com.learn.mycc.core.annotation.ToolRisk;
import com.learn.mycc.core.bean.BeanDefinition;
import com.learn.mycc.core.bean.BeanFactory;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.tool.ToolDefinition;
import com.learn.mycc.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** M8 风险声明：bash / write_file / edit_file 标 HIGH，其余内置工具保持 LOW。 */
class ToolRiskDeclarationTest {

    @Test
    void riskyToolsAreDeclaredHighAndOthersLow() {
        BeanFactory factory = new BeanFactory();
        ToolRegistry registry = new ToolRegistry();
        factory.addBeanPostProcessor(registry);
        factory.register(
                BeanDefinition.from(ApplicationConfig.class),
                BeanDefinition.from(BashTool.class),
                BeanDefinition.from(FileTools.class),
                BeanDefinition.from(SearchTools.class));
        for (Class<?> type : List.of(BashTool.class, FileTools.class, SearchTools.class)) {
            factory.getBean(type);
        }

        ToolDefinition bash = registry.get("bash");
        ToolDefinition writeFile = registry.get("write_file");
        ToolDefinition editFile = registry.get("edit_file");
        ToolDefinition readFile = registry.get("read_file");
        ToolDefinition glob = registry.get("glob");
        ToolDefinition grep = registry.get("grep");
        ToolDefinition searchFiles = registry.get("search_files");

        assertThat(bash.getRisk()).isEqualTo(ToolRisk.HIGH);
        assertThat(writeFile.getRisk()).isEqualTo(ToolRisk.HIGH);
        assertThat(editFile.getRisk()).isEqualTo(ToolRisk.HIGH);
        assertThat(readFile.getRisk()).isEqualTo(ToolRisk.LOW);
        assertThat(glob.getRisk()).isEqualTo(ToolRisk.LOW);
        assertThat(grep.getRisk()).isEqualTo(ToolRisk.LOW);
        assertThat(searchFiles.getRisk()).isEqualTo(ToolRisk.LOW);
    }
}