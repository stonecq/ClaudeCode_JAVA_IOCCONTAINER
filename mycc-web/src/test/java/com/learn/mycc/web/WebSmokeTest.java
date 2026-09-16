package com.learn.mycc.web;

import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.core.context.IocContainer;
import com.learn.mycc.storage.file.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Web 层真实 HTTP 冒烟：经随机端口验证会话列表/新建/删除（发消息需真实 LLM，另测）。 */
@SpringBootTest(classes = {WebApplication.class, WebSmokeTest.TestBeans.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSmokeTest {

    /** 提供 WebController 所需的自研容器（含 SessionStore 与 WebPort）。 */
    @TestConfiguration
    static class TestBeans {
        @Bean
        IocContainer iocContainer() throws Exception {
            IocContainer container = IocContainer.create();
            FileStorage storage = new FileStorage(Files.createTempDirectory("mycc-web-smoke"));
            container.registerSingleton(SessionStore.class, new SessionStore(storage));
            container.registerSingleton(WebPort.class, new WebPort());
            return container;
        }
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void listsCreatesAndDeletesSessionsOverHttp() {
        ResponseEntity<Object[]> empty = rest.getForEntity("/v1/sessions", Object[].class);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody()).isEmpty();

        ResponseEntity<Map> created = rest.postForEntity("/v1/sessions", null, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        String id = (String) created.getBody().get("id");
        assertThat(id).isNotBlank();

        ResponseEntity<Object[]> listed = rest.getForEntity("/v1/sessions", Object[].class);
        assertThat(listed.getBody()).hasSize(1);

        rest.delete("/v1/sessions/" + id);
        ResponseEntity<Object[]> after = rest.getForEntity("/v1/sessions", Object[].class);
        assertThat(after.getBody()).isEmpty();
    }

    @Test
    void indexPageIsServed() {
        ResponseEntity<String> page = rest.getForEntity("/", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("mycc");
    }
}