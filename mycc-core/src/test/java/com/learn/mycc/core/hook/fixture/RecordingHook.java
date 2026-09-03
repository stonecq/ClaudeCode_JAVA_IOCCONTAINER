package com.learn.mycc.core.hook.fixture;

import com.learn.mycc.core.annotation.Component;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;

import java.util.ArrayList;
import java.util.List;

/** 测试用 @Hook 组件：同一事件挂两个订阅者，另挂一个 tool_call_before，用于验证注册表扫描。 */
@Component
public class RecordingHook {

    public static final List<HookEvent> EVENTS = new ArrayList<>();

    public static void clear() {
        EVENTS.clear();
    }

    @Hook(event = HookEventType.SESSION_START)
    public void onSessionStart(HookEvent event) {
        EVENTS.add(event);
    }

    @Hook(event = HookEventType.SESSION_START)
    public void onSessionStartAgain(HookEvent event) {
        EVENTS.add(event);
    }

    @Hook(event = HookEventType.TOOL_CALL_BEFORE)
    public void onToolCallBefore(HookEvent event) {
        EVENTS.add(event);
    }
}
