package com.learn.mycc.memory.hook;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.annotation.Inject;
import com.learn.mycc.core.config.ApplicationConfig;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;
import com.learn.mycc.memory.MemoryStorage;
import com.learn.mycc.memory.MemoryType;

import java.util.ArrayList;
import java.util.List;

@Component
public class MemoryHook {
    private final MemoryStorage storage;

    private final ApplicationConfig config;

    private final String USER_KEY = "mycc_user";

    @Inject
    public MemoryHook(MemoryStorage storage, ApplicationConfig config) {
        this.storage = storage;
        this.config = config;
    }

    @Hook(event = HookEventType.SESSION_START)
    public void systemPromptMemoryHook(HookEvent event) {
        if (event == null || event.payload() == null) {
            return;
        }

        // 1. 校验 payload 是否为 List
        if (event.payload() instanceof List<?> rawList) {

            // 2. 强制转换为 List<String> 以便添加数据
            @SuppressWarnings("unchecked")
            List<String> systemPromptList = (List<String>) rawList;

            // 3. 直接往原数组里添加记忆变量（加了非空判断）
            String sessionMemory = storage.loadMemory(event.sessionId(), MemoryType.SESSION);
            if (sessionMemory != null) systemPromptList.add(sessionMemory);

            String projectMemory = storage.loadMemory(config.getWorkspacePath().toString(), MemoryType.PROJECT);
            if (projectMemory != null) systemPromptList.add(projectMemory);

            String userMemory = storage.loadMemory(USER_KEY, MemoryType.USER);
            if (userMemory != null) systemPromptList.add(userMemory);
        }
    }
}
