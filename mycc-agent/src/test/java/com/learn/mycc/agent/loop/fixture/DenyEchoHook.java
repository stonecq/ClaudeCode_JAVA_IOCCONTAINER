package com.learn.mycc.agent.loop.fixture;

import com.learn.mycc.ai.model.ToolCall;
import com.learn.mycc.core.annotation.Hook;
import com.learn.mycc.core.hook.HookDecision;
import com.learn.mycc.core.hook.HookEvent;
import com.learn.mycc.core.hook.HookEventType;

/** 测试用：在 tool_call_before 拒绝 echo 工具，用于验证 AgentLoop 对被拒调用的拦截流程。 */
public class DenyEchoHook {

    public static final String REASON = "echo 已被禁用";

    @Hook(event = HookEventType.TOOL_CALL_BEFORE)
    public HookDecision denyEcho(HookEvent event) {
        if (event.payload() instanceof ToolCall call && "echo".equals(call.name())) {
            return HookDecision.deny(REASON);
        }
        return HookDecision.ALLOW;
    }
}