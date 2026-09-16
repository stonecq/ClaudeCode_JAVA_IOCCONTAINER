package com.learn.mycc.agent.config;

import com.learn.mycc.agent.loop.AgentLoop;
import com.learn.mycc.agent.session.Session;
import com.learn.mycc.agent.storage.SessionStore;
import com.learn.mycc.ai.spi.LlmProvider;
import com.learn.mycc.compact.Compactor;
import com.learn.mycc.core.annotation.Bean;
import com.learn.mycc.core.annotation.Configuration;
import com.learn.mycc.core.annotation.Scope;
import com.learn.mycc.core.annotation.ScopeType;
import com.learn.mycc.core.hook.HookDispatcher;
import com.learn.mycc.core.tool.ToolRegistry;
import com.learn.mycc.storage.config.ConfigDefaults;
import com.learn.mycc.storage.config.ConfigService;
import com.learn.mycc.ui.InteractionPort;

/**
 * Agent 装配配置：agent 循环以 prototype 作用域交给容器纳管。
 * 会话（{@link Session}）是调用侧值对象而非 bean——调用侧经
 * {@code getBean(AgentLoop.class, session)} 用 args 覆盖绑定；model/maxIterations
 * 从 ConfigService 读取（缺省 deepseek-v4-flash / 10），消除装配根硬编码。
 */
@Configuration
public class AgentConfig {

    @Bean
    @Scope(ScopeType.PROTOTYPE)
    public AgentLoop agentLoop(InteractionPort port, LlmProvider provider, ToolRegistry registry,
                               ConfigService config, SessionStore store, HookDispatcher hooks,
                               Compactor compactor, Session session) {
        return AgentLoop.withToolRegistry(port, provider, registry,
                config.getString(ConfigDefaults.AGENT_MODEL),
                config.getInt(ConfigDefaults.AGENT_MAX_ITERATIONS),
                store, session, hooks, compactor,
                config.getInt(ConfigDefaults.AGENT_MAX_REACTIVE_RETRIES));
    }
}