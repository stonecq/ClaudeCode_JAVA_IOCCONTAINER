package com.learn.mycc.core.hook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookEventTypeTest {

    @Test
    void exposesSixCanonicalEventNames() {
        assertThat(HookEventType.values()).hasSize(6);
        assertThat(HookEventType.SESSION_START.eventName()).isEqualTo("session_start");
        assertThat(HookEventType.SESSION_END.eventName()).isEqualTo("session_end");
        assertThat(HookEventType.TOOL_CALL_BEFORE.eventName()).isEqualTo("tool_call_before");
        assertThat(HookEventType.TOOL_CALL_AFTER.eventName()).isEqualTo("tool_call_after");
        assertThat(HookEventType.ERROR.eventName()).isEqualTo("error");
        assertThat(HookEventType.USER_PROMPT_SUBMIT.eventName()).isEqualTo("user_prompt_submit");
    }
}