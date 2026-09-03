package com.learn.mycc.agent.loop.fixture;

import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.hook.HookEvent;

import java.util.ArrayList;
import java.util.List;

/** 测试用：覆盖全部 6 个事件的记录器，供 AgentLoop 埋点测试断言事件序列。 */
public class HookRecorder {

    public static final List<String> EVENTS = new ArrayList<>();

    public static void clear() {
        EVENTS.clear();
    }

    @Hook(event = "session_start")
    public void onSessionStart(HookEvent event) {
        EVENTS.add(event.type().eventName());
    }

    @Hook(event = "session_end")
    public void onSessionEnd(HookEvent event) {
        EVENTS.add(event.type().eventName());
    }

    @Hook(event = "user_prompt_submit")
    public void onUserPromptSubmit(HookEvent event) {
        EVENTS.add(event.type().eventName());
    }

    @Hook(event = "tool_call_before")
    public void onToolCallBefore(HookEvent event) {
        EVENTS.add(event.type().eventName());
    }

    @Hook(event = "tool_call_after")
    public void onToolCallAfter(HookEvent event) {
        EVENTS.add(event.type().eventName());
    }

    @Hook(event = "error")
    public void onError(HookEvent event) {
        EVENTS.add(event.type().eventName());
    }
}
