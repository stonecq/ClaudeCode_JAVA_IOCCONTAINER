package com.learn.mycc.web;

import com.learn.mycc.ui.AgentApi;
import com.learn.mycc.ui.MessageView;
import com.learn.mycc.ui.SessionView;
import com.learn.mycc.ui.ToolView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Web 层真实 HTTP 冒烟：经随机端口验证会话列表/新建/删除（发消息需真实 LLM，另测）。 */
@SpringBootTest(classes = {WebApplication.class, WebSmokeTest.TestBeans.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSmokeTest {

    /** 提供 WebController 所需的 AgentApi 与 WebPort。 */
    @TestConfiguration
    static class TestBeans {
        @Bean
        AgentApi agentApi() {
            return new FakeAgentApi();
        }

        @Bean
        WebPort webPort() {
            return new WebPort();
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

    /** 内存版 AgentApi 假实现。 */
    static final class FakeAgentApi implements AgentApi {
        private final Map<String, List<MessageView>> sessions = new LinkedHashMap<>();
        private int counter = 0;

        @Override
        public List<SessionView> listSessions() {
            return sessions.entrySet().stream()
                    .map(e -> new SessionView(e.getKey(), "（空对话）", 0L))
                    .toList();
        }

        @Override
        public String createSession() {
            String id = "sess-" + (++counter);
            sessions.put(id, List.of());
            return id;
        }

        @Override
        public void deleteSession(String id) {
            sessions.remove(id);
        }

        @Override
        public List<MessageView> history(String id) {
            return sessions.getOrDefault(id, List.of());
        }

        @Override
        public void replay(String id) {
        }

        @Override
        public void chat(String sessionId, String userMessage) {
        }

        @Override
        public List<ToolView> listTools() {
            return new ArrayList<>();
        }
    }
}